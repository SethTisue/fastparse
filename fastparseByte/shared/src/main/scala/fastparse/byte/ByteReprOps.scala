package scala.meta.internal.fastparse.byte
import acyclic.file
import scala.meta.internal.fastparse.utils.{ElemSetHelper, Generator, ParserInput, ReprOps}
import scala.meta.internal.fastparse.utils.Utils.HexUtils
import scodec.bits.ByteVector

object ByteBitSetHelper extends ElemSetHelper[Byte] {
  def toInt(a: Byte): Int = a
  def ordering = implicitly[Ordering[Byte]]
  def toLowerCase(in: Byte) = in
  def generateValues(f: Generator.Callback[Byte]) = {
    var i = Byte.MinValue.toInt
    while(i <= Byte.MaxValue){
      f(i.toByte)
      i += 1
    }
  }
}

object ByteReprOps extends ReprOps[Byte, ByteVector] {
  private[this] type Bytes = ByteVector
  def fromArray(input: Array[Byte]) = ByteVector(input:_*)
  def toArray(input: ByteVector) = input.toArray
  def fromSeq(input: Seq[Byte]) = ByteVector(input:_*)

  private def ByteToHex(b: Byte) = {
    def singleHexChar(b: Int) = b match{
      case 0  => '0'
      case 1  => '1'
      case 2  => '2'
      case 3  => '3'
      case 4  => '4'
      case 5  => '5'
      case 6  => '6'
      case 7  => '7'
      case 8  => '8'
      case 9  => '9'
      case 10 => 'a'
      case 11 => 'b'
      case 12 => 'c'
      case 13 => 'd'
      case 14 => 'e'
      case 15 => 'f'
    }

    s"${singleHexChar((b & 0xf0) >> 4)}${singleHexChar(b & 15)}"
  }

  def prettyPrint(input: Bytes): String = input.toArray.map(ByteToHex).mkString(" ")
  def literalize(input: Bytes): String = '"' + prettyPrint(input) + '"'

  def errorMessage(input: ParserInput[Byte, Bytes], expected: String, idx: Int): String = {
    val locationCode = {
      val first = input.slice(idx - 20, idx)
      val last = input.slice(idx, idx + 20)

      prettyPrint(first) + prettyPrint(last) + "\n" + (" " * length(first)) + "^"
    }
    val literal = literalize(input.slice(idx, idx + 20))
    s"found $literal, expected $expected at index $idx\n$locationCode"
  }

  def prettyIndex(input: ParserInput[Byte, Bytes], index: Int): String = String.valueOf(index)

  def slice(value: Bytes, start: Int, end: Int) = {
    value.slice(math.max(0, start), math.min(end, value.length))
  }

  def apply(value: Bytes, i: Int) = value(i)

  def length(value: Bytes) = value.length.toInt

  def fromSingle(input: Byte) = ByteVector(input)

  def flatten(input: Seq[Bytes]): Bytes = {
    var current = ByteVector.empty
    for(i <- input) current = current ++ i
    current
  }
}
