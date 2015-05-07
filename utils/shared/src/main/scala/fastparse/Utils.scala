package fastparse

import scala.annotation.switch
import acyclic.file
import scala.reflect.macros.blackbox.Context

import scala.language.experimental.macros

object Utils {
  def preCompute(f: Char => Boolean): fastparse.Utils.CharBitSet = macro impl

  def impl(c: Context)(f: c.Expr[Char => Boolean]): c.Expr[fastparse.Utils.CharBitSet] = {
    import c.universe._
    val evaled = c.eval(c.Expr[Char => Boolean](c.untypecheck(f.tree.duplicate)))
    val (first, last, array) = CharBitSet.compute((Char.MinValue to Char.MaxValue).filter(evaled))
    val txt = CharBitSet.ints2Hex(array)
    c.Expr[CharBitSet](q"""
      new fastparse.Utils.CharBitSet(fastparse.Utils.CharBitSet.hex2Ints($txt), $first, $last)
    """)
  }
  /**
   * Convert a string to a C&P-able literal. Basically
   * copied verbatim from the uPickle source code.
   */
  def literalize(s: String, unicode: Boolean = true) = {
    val sb = new StringBuilder
    sb.append('"')
    var i = 0
    val len = s.length
    while (i < len) {
      (s.charAt(i): @switch) match {
        case '"' => sb.append("\\\"")
        case '\\' => sb.append("\\\\")
        case '\b' => sb.append("\\b")
        case '\f' => sb.append("\\f")
        case '\n' => sb.append("\\n")
        case '\r' => sb.append("\\r")
        case '\t' => sb.append("\\t")
        case c =>
          if (c < ' ' || (c > '~' && unicode)) sb.append("\\u%04x" format c.toInt)
          else sb.append(c)
      }
      i += 1
    }
    sb.append('"')

  }

  def isPrintableChar(c: Char): Boolean = {
    val block = Character.UnicodeBlock.of(c)
    !Character.isISOControl(c) && !Character.isSurrogate(c) && block != null && block != Character.UnicodeBlock.SPECIALS
  }

  object CharBitSet{
    val hexChars = Seq(
      '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
      'a', 'b', 'c', 'd', 'e', 'f'
    )
    def hex2Int(hex: String): Int = {
      var res = 0
      for(i <- 0 until hex.length){
        res += hexChars.indexOf(hex(i)) << (4 * (7 - i))
      }
      res
    }
    def hex2Ints(hex: String): Array[Int] = {
      val res = for {
        i <- 0 to hex.length - 1 by 8
        // parseUnsignedInt not implemented in Scala.js
        // java.lang.Long.parseLong also misbehaves
      } yield hex2Int(hex.slice(i, i+8))
      res.toArray
    }

    def ints2Hex(ints: Array[Int]): String = {
      val res = for(int <- ints) yield {
        val s = Integer.toHexString(int)
        "0" * (8-s.length) + s
      }
      res.mkString
    }
    def compute(chars: Seq[Char]) = {
      val first = chars.min
      val last = chars.max
      val span = last - first
      val array = new Array[Int](span / 32 + 1)
      for(c <- chars) array((c - first) >> 5) |= 1 << ((c - first) & 31)
      (first, last, array)
    }
    def apply(chars: Seq[Char]) = {
      val (first, last, array) = compute(chars)
      for(c <- chars) array((c - first) >> 5) |= 1 << ((c - first) & 31)
      new CharBitSet(array, first, last)
    }
  }
  /**
   * A small, fast implementation of a bitset packing up to 65k Chars
   * into 2k Ints (8k Bytes) but using less if the range of inputs
   * is smaller.
   *
   * Empirically seems to be a hell of a lot faster than immutable.Bitset,
   * making the resultant parser up to 2x faster!
   */
  class CharBitSet(array: Array[Int], first: Int, last: Int) extends (Char => Boolean){
    def apply(c: Char) = {
      if (c > last || c < first) false
      else {
        val offset = c - first
        (array(offset >> 5) & 1 << (offset & 31)) != 0
      }
    }
  }
}
