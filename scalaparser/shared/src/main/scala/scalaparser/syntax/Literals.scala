package scalaparser.syntax

import acyclic.file
import fastparse.Parser.CharsWhile
import fastparse._
import fastparse.preds.CharPredicates
import Basic._
import Identifiers._

trait Literals { l =>
  def Block: R0
  def WL: R0
  def Pattern: R0
  object Literals{
    import Basic._
    val Float = {
      def Thing = R( DecNum ~ Exp.? ~ FloatType.? )
      def Thing2 = R( "." ~ Thing | Exp ~ FloatType.? | Exp.? ~ FloatType )
      R( "." ~ Thing | DecNum ~ Thing2 )
    }

    val Int = R( (HexNum | DecNum) ~ CharIn("Ll").? )

    val Bool = R( Key.W("true") | Key.W("false")  )

    // Comments cannot have cuts in them, because they appear before every
    // terminal node. That means that a comment before any terminal will
    // prevent any backtracking from working, which is not what we want!
    val CommentChunk = R( CharsWhile(!"/*".contains(_), min = 1) | MultilineComment | !"*/" ~ Parser.AnyChar )
    val MultilineComment: R0 = R( "/*" ~ CommentChunk.rep ~ "*/" )
    val LineComment = R( "//" ~ (CharsWhile(!"\n\r".contains(_), min = 1) | !Basic.Newline ~ Parser.AnyChar).rep ~ &(Basic.Newline | Parser.End) )
    val Comment: R0 = R( MultilineComment | LineComment )

    val Null = Key.W("null")

    val OctalEscape = R( Digit ~ Digit.? ~ Digit.? )
    val EscapedChars = R( "\\" ~! (CharIn("""btnfr'\"]""") | OctalEscape | UnicodeEscape ) )

    // Note that symbols can take on the same values as keywords!
    val Symbol = R( Identifiers.PlainId | Identifiers.Keywords )

    val Char = {
      // scalac 2.10 crashes if PrintableChar below is substituted by its body
      def PrintableChar = CharPred(CharPredicates.isPrintableChar)

      R( (EscapedChars | PrintableChar) ~ "'" )
    }

    class InterpCtx(interp: Option[R0]){
      val Literal = R( ("-".? ~ (Float | Int)) | Bool | String | "'" ~! (Char | Symbol) | Null )
      val Interp = interp match{
        case None => Parser.Fail
        case Some(p) => "$" ~ Identifiers.PlainIdNoDollar | ("${" ~ p ~ WL ~ "}") | "$$"
      }


      val TQ = R( "\"\"\"" )
      /**
       * Helper to quickly gobble up large chunks of un-interesting
       * characters. We break out conservatively, even if we don't know
       * it's a "real" escape sequence: worst come to worst it turns out
       * to be a dud and we go back into a CharsChunk next rep
       */
      val CharsChunk = CharsWhile(!"\n\"\\$".contains(_), min = 1)
      val TripleChars = R( (CharsChunk | Interp | "\"".? ~ "\"".? ~ !"\"" ~ Parser.AnyChar).rep )
      val TripleTail = R( TQ ~ "\"".rep )
      val SingleChars = R( (CharsChunk | Interp | EscapedChars | !CharIn("\n\"") ~ Parser.AnyChar).rep )
      val String = {
        R {
          (Id ~ TQ ~! TripleChars ~ TripleTail) |
          (Id ~ "\"" ~! SingleChars  ~ "\"") |
          (TQ ~! NoInterp.TripleChars ~ TripleTail) |
          ("\"" ~! NoInterp.SingleChars ~ "\"")
        }
      }

    }
    object NoInterp extends InterpCtx(None)
    object Pat extends InterpCtx(Some(l.Pattern))
    object Expr extends InterpCtx(Some(Block))


  }
}
