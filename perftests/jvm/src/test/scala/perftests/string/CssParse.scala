package perftests.string

import perftests.Utils
import utest._
import scala.meta.internal.fastparse.all._
object CssParse extends TestSuite {
  val bootstrapStream = getClass.getResourceAsStream("/bootstrap.css")
  val bootstrapSource = scala.io.Source.fromInputStream(bootstrapStream).mkString
  def bootstrapIterator(size: Int) = bootstrapSource.grouped(size)
  val parser = cssparse.CssRulesParser.ruleList

  val tests = Tests {
    'Bootstrap - {
      Utils.benchmarkAll(
        "CssParse",
        parser,
        bootstrapSource, None,
        bootstrapIterator
      )
    }
  }
}
