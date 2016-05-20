package hmda.validation.rules.lar.validity

import hmda.model.fi.ts.TransmittalSheet
import hmda.validation.rules.EditCheck
import hmda.validation.rules.ts.TsEditCheckSpec
import hmda.validation.rules.ts.validity.V120
import org.scalacheck.Gen

class V120Spec extends TsEditCheckSpec {

  property("passes with properly formed number") {
    forAll(tsGen) { ts =>
      whenever(ts.contact.phone.length == 12) {
        ts.mustPass
      }
    }
  }

  property("fail with other seperators") {
    forAll(tsGen, badPhoneNumberGen) { (ts: TransmittalSheet, x: String) =>
      val badContact = ts.contact.copy(phone = x)
      val badTs = ts.copy(contact = badContact)
      badTs.mustFail
    }
  }

  override def check: EditCheck[TransmittalSheet] = V120

  implicit def badPhoneNumberGen: Gen[String] = {
    for {
      p1 <- Gen.numStr
      p2 <- Gen.numStr
      p3 <- Gen.numStr
      sep <- Gen.oneOf(List(".", "/", ""))
    } yield List(p1.take(3).toString, sep, p2.take(3).toString, sep, p3.take(4).toString).mkString
  }

}
