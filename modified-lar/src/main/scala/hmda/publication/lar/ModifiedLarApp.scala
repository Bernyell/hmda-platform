package hmda.publication.lar

import akka.Done
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.{ ActorRef, ActorSystem }
import akka.actor.{ ActorSystem => ClassicActorSystem }
import akka.kafka.scaladsl.{ Committer, Consumer }
import akka.kafka.{ CommitterSettings, ConsumerMessage, ConsumerSettings, Subscriptions }
import akka.stream.Materializer
import akka.stream.scaladsl._
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import hmda.census.records._
import hmda.messages.pubsub.{ HmdaGroups, HmdaTopics }
import hmda.model.census.Census
import hmda.model.filing.submission.SubmissionId
import hmda.publication.KafkaUtils._
import hmda.publication.lar.publication._
import hmda.query.repository.ModifiedLarRepository
import hmda.util.BankFilterUtils._
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.LoggerFactory
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }

// Entrypoint
// $COVERAGE-OFF$
object ModifiedLarApp extends App {

  def submitForPersistence(
                            modifiedLarPublisher: ActorRef[PersistToS3AndPostgres]
                          )(untypedSubmissionId: String)(implicit timeout: Timeout, ec: ExecutionContext): Future[Done] = {
    val submissionId = SubmissionId(untypedSubmissionId)
    if (!filterBankWithLogging(submissionId.lei) || filterQuarterlyFiling(submissionId))
      Future.successful(Done.done())
    else {
      val futRes: Future[PersistModifiedLarResult] =
        modifiedLarPublisher ? ((ref: ActorRef[PersistModifiedLarResult]) => PersistToS3AndPostgres(submissionId, ref))
      // bubble up failure if there is any (this is done to prevent a commit to Kafka from happening
      // when used in Kafka consumer Akka Stream
      futRes.map(result => result.status).flatMap {
        case UploadSucceeded         => Future(Done.done())
        case UploadFailed(exception) => Future.failed(exception)
      }
    }
  }

  val log = LoggerFactory.getLogger("hmda")

  log.info(
    """
      |___  ___          _ _  __ _          _   _       ___  ______
      ||  \/  |         | (_)/ _(_)        | | | |     / _ \ | ___ \
      || .  . | ___   __| |_| |_ _  ___  __| | | |    / /_\ \| |_/ /
      || |\/| |/ _ \ / _` | |  _| |/ _ \/ _` | | |    |  _  ||    /
      || |  | | (_) | (_| | | | | |  __/ (_| | | |____| | | || |\ \
      |\_|  |_/\___/ \__,_|_|_| |_|\___|\__,_| \_____/\_| |_/\_| \_|
      |
    """.stripMargin
  )

  implicit val classicSystem: ClassicActorSystem = ClassicActorSystem("modified-lar-system")
  implicit val system: ActorSystem[_]            = classicSystem.toTyped
  implicit val materializer: Materializer        = Materializer(system)
  implicit val ec: ExecutionContext              = system.executionContext
  implicit val timeout: Timeout                  = Timeout(1.hour)

  val config      = ConfigFactory.load()
  val kafkaConfig = config.getConfig("akka.kafka.consumer")
  val parallelism = config.getInt("hmda.lar.modified.parallelism")

  val censusTractMap2018: Map[String, Census] =
    CensusRecords.indexedTract2018

  val censusTractMap2019: Map[String, Census] =
    CensusRecords.indexedTract2019

  // database configuration is located in `common` project
  val databaseConfig = DatabaseConfig.forConfig[JdbcProfile]("db")
  val repo           = new ModifiedLarRepository(databaseConfig)
  val modifiedLarPublisher =
    classicSystem.spawn(ModifiedLarPublisher.behavior(censusTractMap2018, censusTractMap2019, repo), ModifiedLarPublisher.name)
  val processKafkaRecord: String => Future[Done] =
    submitForPersistence(modifiedLarPublisher)

  val consumerSettings: ConsumerSettings[String, String] =
    ConsumerSettings(kafkaConfig, new StringDeserializer, new StringDeserializer)
      .withBootstrapServers(kafkaHosts)
      .withGroupId(HmdaGroups.modifiedLarGroup)
      .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest")

  val streamCompleted: Future[Done] =
    Consumer
      .committableSource(consumerSettings, Subscriptions.topics(HmdaTopics.signTopic, HmdaTopics.modifiedLarTopic))
      .mapAsync(parallelism) { msg =>
        def processMsg(): Future[ConsumerMessage.CommittableOffset] = {
          log.info(s"Received a message - key: ${msg.record.key()}, value: ${msg.record.value()}")
          processKafkaRecord(msg.record.value().trim).map(_ => msg.committableOffset)
        }
        akka.pattern.retry(() => processMsg(), 1, 90.seconds)(ec, classicSystem.scheduler)
      }
      .toMat(Committer.sink(CommitterSettings(classicSystem).withParallelism(parallelism * 2)))(Keep.both)
      .mapMaterializedValue { case (control, completed) => control.drainAndShutdown(completed) }
      .run()

  streamCompleted.onComplete {
    case Failure(exception) =>
      log.error("Kafka consumer infinite stream failed", exception)
      classicSystem
        .terminate()
        .foreach(_ => sys.exit(1)) // The Cassandra connection stays up and prevents the app from shutting down even when the Kafka stream is not active, so we explicitly shut the application down

    case Success(res) =>
      log.error("Kafka consumer infinite stream completed, an infinite stream should not complete")
      classicSystem
        .terminate()
        .foreach(_ => sys.exit(2))
  }

}
// $COVERAGE-ON$