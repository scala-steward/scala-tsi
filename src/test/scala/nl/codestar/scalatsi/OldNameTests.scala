package nl.codestar.scalatsi

import com.scalatsi.convertToStringHasWrapperForVerb

import scala.annotation.nowarn
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

@nowarn("cat=deprecation")
class OldNameTests extends AnyWordSpec with Matchers with DefaultTSTypes {
  "Old names should still compile" forWord {
    "String" in { "implicitly[TSType[String]]" should compile }
    "dsl" in {
      """
         import nl.codestar.scalatsi.dsl._
         case class Foo(bar: String)
         val first: TSIType[Foo] = TSType.fromCaseClass[Foo]
         val second: TSIType[Foo] =
         TSType.fromCaseClass[Foo] + ("extraField" -> classOf[String])
      """ should compile
    }
  }
}
