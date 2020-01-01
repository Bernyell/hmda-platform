package hmda.dashboard.models

import io.circe.{Decoder, Encoder, HCursor}

case class SignsForLastDaysAggregationResponse(aggregations: Seq[SignsForLastDays])

object SignsForLastDaysAggregationResponse {
  private object constants {
    val Results = "results"
  }

  implicit val encoder: Encoder[SignsForLastDaysAggregationResponse] =
    Encoder.forProduct1(constants.Results)(aggR =>
      aggR.aggregations)

  implicit val decoder: Decoder[SignsForLastDaysAggregationResponse] = (c: HCursor) =>
    for {
      a <- c.downField(constants.Results).as[Seq[SignsForLastDays]]
    } yield SignsForLastDaysAggregationResponse(a)
}