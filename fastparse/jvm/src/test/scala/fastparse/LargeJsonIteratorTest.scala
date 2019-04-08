package scala.meta.internal.fastparse

import scala.meta.internal.fastparse.all._
import utest._
import scala.meta.internal.fastparse.JsonTests._

import scala.io.Source

object LargeJsonIteratorTest extends TestSuite {
  def source = io.Source.fromInputStream(getClass.getResourceAsStream("/test.json"))

  val tests = Tests {
    'large - {
      val Parsed.Success(_, i) = jsonExpr.parseIterator(source.getLines().map(_.repr.toString))
      val expectedIndex = source.getLines().map(_.length).sum
      assert(i == expectedIndex)
    }

    'maxInnerLength - {
      val loggedInput = new IteratorParserInput(source.getLines().map(_.repr.toString)) {
        var maxInnerLength = 0
        override def dropBuffer(index: Int) = {
          maxInnerLength = math.max(maxInnerLength, this.innerLength)
          super.dropBuffer(index)
        }
      }

      val Parsed.Success(_, i) = jsonExpr.parseInput(loggedInput)
      println(s"Size: ${source.getLines().map(_.length).sum}")
      println(s"Max buffer length: ${loggedInput.maxInnerLength}")
    }
  }
}
