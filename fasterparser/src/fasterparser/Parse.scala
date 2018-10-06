package fasterparser

import scala.annotation.{switch, tailrec}
import scala.collection.mutable
import language.experimental.macros
import reflect.macros.blackbox.Context

object Parse {

  type P[+T] = Parsed[T]
  def P[T](t: Parsed[T])(implicit ctx: Ctx[Any], name: sourcecode.Name): Parsed[T] = macro pMacro[T]

  def pMacro[T: c.WeakTypeTag](c: Context)
                              (t: c.Expr[Parsed[T]])
                              (ctx: c.Expr[Ctx[Any]], name: c.Expr[sourcecode.Name]): c.Expr[Parsed[T]] = {

    import c.universe._
    reify[Parsed[T]]{
      t.splice match{
        case f: Parsed.Failure =>
          f.stack = name.splice.value :: f.stack
          f
        case s => s
      }
    }
  }

  implicit def LiteralStr(s: String)(implicit ctx: Ctx[_]): Parsed[Unit] = {

    if (ctx.input.startsWith(s, ctx.success.index)) ctx.freshSuccess((), ctx.success.index + s.length)
    else ctx.freshFailure()
  }
  def literalStrMacro(c: Context)(s: c.Expr[String])(ctx: c.Expr[Ctx[Any]]): c.Expr[Parsed[Unit]] = {
    import c.universe._
    reify{
      val s1 = s.splice
      if (ctx.splice.input.startsWith(s1, ctx.splice.success.index)) ctx.splice.freshSuccess((), ctx.splice.success.index + s1.length)
      else ctx.splice.freshFailure()
    }
  }

  def cutMacro[T: c.WeakTypeTag](c: Context)(ctx: c.Expr[Ctx[Any]]): c.Expr[Parsed[T]] = {
    import c.universe._
    reify{
      c.prefix.splice match{
        case f: Parsed.Failure => ctx.splice.prepareFailure(f.index)
        case p: Parsed.Success[T] => ctx.splice.prepareSuccess(p.value, cut = true)
      }
    }
  }
  def cutStrMacro(c: Context)(ctx: c.Expr[Ctx[Any]]): c.Expr[Parsed[Unit]] = {
    import c.universe._
    val lhs = literalStrMacro(c)(reify(c.prefix.asInstanceOf[Expr[EagerOpsStr]].splice.parse0))(ctx)
    reify{
      lhs.splice match{
        case f: Parsed.Failure => ctx.splice.prepareFailure(f.index)
        case p: Parsed.Success[Unit] => ctx.splice.prepareSuccess(p.value, cut = true)
      }
    }
  }

  implicit class EagerOpsStr(val parse0: String) extends AnyVal {

    def ~/[V, R](other: Parsed[V])
                   (implicit s: Implicits.Sequencer[Unit, V, R],
                    whitespace: Ctx[Any] => Parsed[Unit],
                    ctx: Ctx[Any]): Parsed[R] = macro parsedSequenceCutString[V, R]


    def /[T](implicit  ctx: Ctx[_]): Parsed[Unit] = macro cutStrMacro

    def ~[V, R](other: Parsed[V])
                  (implicit s: Implicits.Sequencer[Unit, V, R],
                   whitespace: Ctx[Any] => Parsed[Unit],
                   ctx: Ctx[Any]): Parsed[R] = macro parsedSequenceString[V, R]


    def ~~/[V, R](other: Parsed[V])
                    (implicit s: Implicits.Sequencer[Unit, V, R],
                     ctx: Ctx[Any]): Parsed[R] = macro parsedSequenceCutString1[V, R]


    def ~~[V, R](other: Parsed[V])
                   (implicit s: Implicits.Sequencer[Unit, V, R],
                    ctx: Ctx[Any]): Parsed[R] = macro parsedSequenceString1[V, R]
  }

  def parsedSequence0[T: c.WeakTypeTag, V: c.WeakTypeTag, R: c.WeakTypeTag]
                     (c: Context)
                     (lhs: c.Expr[Parsed[T]], other: c.Expr[Parsed[V]], cut: Boolean)
                     (s: c.Expr[Implicits.Sequencer[T, V, R]],
                      whitespace: c.Expr[Ctx[Any] => Parsed[Unit]],
                      ctx: c.Expr[Ctx[Any]]): c.Expr[Parsed[R]] = {
    import c.universe._

    val cut1 = c.Expr[Boolean](if(cut) q"true" else q"false")
    reify {
      lhs.splice match {
        case f: Parsed.Failure => f
        case p: Parsed.Success[T] =>
          val pValue = p.value
          val pCut = p.cut
          whitespace.splice(ctx.splice) match {
            case f: Parsed.Failure => f
            case _: Parsed.Success[_] =>
              other.splice match {
                case f: Parsed.Failure =>
                  ctx.splice.prepareFailure(f.index, cut = f.cut | cut1.splice | pCut)
                case p1: Parsed.Success[V] =>
                  ctx.splice.prepareSuccess(s.splice.apply(pValue, p1.value), cut = pCut | cut1.splice | p1.cut)

              }
          }
      }
    }
  }

  def parsedSequence00[T: c.WeakTypeTag, V: c.WeakTypeTag, R: c.WeakTypeTag]
                     (c: Context)
                     (lhs: c.Expr[Parsed[T]], other: c.Expr[Parsed[V]], cut: Boolean)
                     (s: c.Expr[Implicits.Sequencer[T, V, R]],
                      ctx: c.Expr[Ctx[Any]]): c.Expr[Parsed[R]] = {
    import c.universe._

    val cut1 = c.Expr[Boolean](if(cut) q"true" else q"false")
    reify {
      lhs.splice match {
        case f: Parsed.Failure => f
        case p: Parsed.Success[T] =>
          val pValue = p.value
          val pCut = p.cut
          other.splice match {
            case f: Parsed.Failure =>
              ctx.splice.prepareFailure(f.index, cut = f.cut | cut1.splice | pCut)
            case p1: Parsed.Success[V] =>
              ctx.splice.prepareSuccess(s.splice.apply(pValue, p1.value), cut = pCut | cut1.splice | p1.cut)

          }
      }
    }
  }

  def parsedSequenceString[V: c.WeakTypeTag, R: c.WeakTypeTag]
                    (c: Context)
                    (other: c.Expr[Parsed[V]])
                    (s: c.Expr[Implicits.Sequencer[Unit, V, R]],
                     whitespace: c.Expr[Ctx[Any] => Parsed[Unit]],
                     ctx: c.Expr[Ctx[Any]]): c.Expr[Parsed[R]] = {
    import c.universe._
    val lhs = literalStrMacro(c)(reify(c.prefix.asInstanceOf[Expr[EagerOpsStr]].splice.parse0))(ctx)
    parsedSequence0[Unit, V, R](c)(lhs, other, false)(s, whitespace, ctx)
  }
  def parsedSequenceCutString[V: c.WeakTypeTag, R: c.WeakTypeTag]
                      (c: Context)
                      (other: c.Expr[Parsed[V]])
                      (s: c.Expr[Implicits.Sequencer[Unit, V, R]],
                       whitespace: c.Expr[Ctx[Any] => Parsed[Unit]],
                       ctx: c.Expr[Ctx[Any]]): c.Expr[Parsed[R]] = {
    import c.universe._
    val lhs = literalStrMacro(c)(reify(c.prefix.asInstanceOf[Expr[EagerOpsStr]].splice.parse0))(ctx)
    parsedSequence0[Unit, V, R](c)(lhs, other, true)(s, whitespace, ctx)
  }
  def parsedSequenceString1[V: c.WeakTypeTag, R: c.WeakTypeTag]
                    (c: Context)
                    (other: c.Expr[Parsed[V]])
                    (s: c.Expr[Implicits.Sequencer[Unit, V, R]],
                     ctx: c.Expr[Ctx[Any]]): c.Expr[Parsed[R]] = {
    import c.universe._
    val lhs = literalStrMacro(c)(reify(c.prefix.asInstanceOf[Expr[EagerOpsStr]].splice.parse0))(ctx)
    parsedSequence00[Unit, V, R](c)(lhs, other, false)(s, ctx)
  }
  def parsedSequenceCutString1[V: c.WeakTypeTag, R: c.WeakTypeTag]
                      (c: Context)
                      (other: c.Expr[Parsed[V]])
                      (s: c.Expr[Implicits.Sequencer[Unit, V, R]],
                       ctx: c.Expr[Ctx[Any]]): c.Expr[Parsed[R]] = {
    import c.universe._
    val lhs = literalStrMacro(c)(reify(c.prefix.asInstanceOf[Expr[EagerOpsStr]].splice.parse0))(ctx)
    parsedSequence00[Unit, V, R](c)(lhs, other, true)(s, ctx)
  }
  def parsedSequence[T: c.WeakTypeTag, V: c.WeakTypeTag, R: c.WeakTypeTag]
                    (c: Context)
                    (other: c.Expr[Parsed[V]])
                    (s: c.Expr[Implicits.Sequencer[T, V, R]],
                     whitespace: c.Expr[Ctx[Any] => Parsed[Unit]],
                     ctx: c.Expr[Ctx[Any]]): c.Expr[Parsed[R]] = {
    import c.universe._
    val lhs = reify{c.prefix.asInstanceOf[Expr[EagerOps[T]]].splice.parse0}
    parsedSequence0[T, V, R](c)(lhs, other, false)(s, whitespace, ctx)
  }
  def parsedSequenceCut[T: c.WeakTypeTag, V: c.WeakTypeTag, R: c.WeakTypeTag]
                    (c: Context)
                    (other: c.Expr[Parsed[V]])
                    (s: c.Expr[Implicits.Sequencer[T, V, R]],
                     whitespace: c.Expr[Ctx[Any] => Parsed[Unit]],
                     ctx: c.Expr[Ctx[Any]]): c.Expr[Parsed[R]] = {
    import c.universe._
    val lhs = reify{c.prefix.asInstanceOf[Expr[EagerOps[T]]].splice.parse0}
    parsedSequence0[T, V, R](c)(lhs, other, true)(s, whitespace, ctx)
  }
  def parsedSequence1[T: c.WeakTypeTag, V: c.WeakTypeTag, R: c.WeakTypeTag]
                    (c: Context)
                    (other: c.Expr[Parsed[V]])
                    (s: c.Expr[Implicits.Sequencer[T, V, R]],
                     ctx: c.Expr[Ctx[Any]]): c.Expr[Parsed[R]] = {
    import c.universe._
    val lhs = reify{c.prefix.asInstanceOf[Expr[EagerOps[T]]].splice.parse0}
    parsedSequence00[T, V, R](c)(lhs, other, false)(s, ctx)
  }
  def parsedSequenceCut1[T: c.WeakTypeTag, V: c.WeakTypeTag, R: c.WeakTypeTag]
                    (c: Context)
                    (other: c.Expr[Parsed[V]])
                    (s: c.Expr[Implicits.Sequencer[T, V, R]],
                     ctx: c.Expr[Ctx[Any]]): c.Expr[Parsed[R]] = {
    import c.universe._
    val lhs = reify{c.prefix.asInstanceOf[Expr[EagerOps[T]]].splice.parse0}
    parsedSequence00[T, V, R](c)(lhs, other, true)(s, ctx)
  }


  implicit def EagerOps[T](parse0: Parsed[T]) = new EagerOps(parse0)
  class EagerOps[T](val parse0: Parsed[T]) {

    def ~/[V, R](other: Parsed[V])
                (implicit s: Implicits.Sequencer[T, V, R],
                 whitespace: Ctx[Any] => Parsed[Unit],
                 ctx: Ctx[Any]): Parsed[R] = macro parsedSequenceCut[T, V, R]

    def /(implicit  ctx: Ctx[_]): Parsed[T] = macro cutMacro[T]

    def ~[V, R](other:  Parsed[V])
                  (implicit s: Implicits.Sequencer[T, V, R],
                   whitespace: Ctx[Any] => Parsed[Unit],
                    ctx: Ctx[Any]): Parsed[R] = macro parsedSequence[T, V, R]


    def ~~/[V, R](other: Parsed[V])
                    (implicit s: Implicits.Sequencer[T, V, R],
                      ctx: Ctx[Any]): Parsed[R] = macro parsedSequenceCut1[T, V, R]


    def ~~[V, R](other: Parsed[V])
                   (implicit s: Implicits.Sequencer[T, V, R],
                     ctx: Ctx[Any]): Parsed[R] = macro parsedSequence1[T, V, R]
  }


  def eitherMacro[S: c.WeakTypeTag, T: c.WeakTypeTag, V >: T: c.WeakTypeTag]
                 (c: Context)
                 (other: c.Expr[Parsed[V]]): c.Expr[Parsed[V]] = {
    import c.universe._

    val q"fasterparser.Parse.ByNameOps[$k, $v]($parse0)($conv, $ctx)" = c.prefix.tree
    reify {
      val ctx1 = c.Expr[Ctx[Any]](ctx).splice
      val startPos = ctx1.success.index
      val conv1 = c.Expr[S => Parsed[T]](conv).splice
      val parse00 = c.Expr[S](parse0).splice
      conv1(parse00) match {
        case p: Parsed.Success[T] => p
        case f: Parsed.Failure =>
          ctx1.success.index = startPos
          if (f.cut) f
          else other.splice match{
            case p: Parsed.Success[V] => p
            case f: Parsed.Failure => ctx1.freshFailure(startPos)
          }
      }
    }
  }
  def captureMacro[S: c.WeakTypeTag, T: c.WeakTypeTag](c: Context): c.Expr[Parsed[String]] = {
    import c.universe._

    val q"fasterparser.Parse.ByNameOps[$k, $v]($parse0)($conv, $ctx)" = c.prefix.tree

    reify {
      val ctx1 = c.Expr[Ctx[Any]](ctx).splice
      val startPos = ctx1.success.index
      val conv1 = c.Expr[S => Parsed[T]](conv).splice
      val parse00: S = c.Expr[S](parse0).splice

      conv1(parse00) match{
        case f: Parsed.Failure => f
        case s: Parsed.Success[_] => ctx1.freshSuccess(ctx1.input.substring(startPos, ctx1.success.index))
      }
    }
  }
  implicit class ByNameOps[S, T](parse0: => S)(implicit val conv: S => Parsed[T], val ctx: Ctx[Any]){
    def parse00 = parse0
    def |[V >: T](other: Parsed[V]): Parsed[V] = macro eitherMacro[S, T, V]
    def repX[V](implicit repeater: Implicits.Repeater[T, V]): Parsed[V] = repX(sep=null)
    def repX[V](min: Int = 0,
               max: Int = Int.MaxValue,
               exactly: Int = -1,
               sep: => Parsed[_] = null)(implicit repeater: Implicits.Repeater[T, V]): Parsed[V] = {


      val acc = repeater.initial
      val actualMin = if(exactly == -1) min else exactly
      val actualMax = if(exactly == -1) max else exactly
      def end(successIndex: Int, failureIndex: Int, count: Int) = {
        if (count < actualMin) ctx.prepareFailure(failureIndex)
        else ctx.prepareSuccess(repeater.result(acc), successIndex)
      }
      @tailrec def rec(startIndex: Int, count: Int, precut: Boolean): Parsed[V] = {
        if (count == 0 && actualMax == 0) ctx.prepareSuccess(repeater.result(acc), startIndex)
        else conv(parse0) match{
          case f: Parsed.Failure =>

            if (f.cut | precut) f
            else end(startIndex, startIndex, count)


          case s: Parsed.Success[T] =>
            val beforeSepIndex = ctx.success.index
            repeater.accumulate(s.value, acc)
            if (count + 1 == actualMax) end(beforeSepIndex, beforeSepIndex, count + 1)
            else sep match{
              case null => rec(beforeSepIndex, count+1, false)
              case s: Parsed.Success[_] => rec(beforeSepIndex, count+1, s.cut)
              case f: Parsed.Failure =>
                if (f.cut) ctx.prepareFailure(beforeSepIndex)
                else end(beforeSepIndex, beforeSepIndex, count+1)
            }
        }
      }

      val res = rec(ctx.success.index, 0, false)

      res
    }
    def rep[V](implicit repeater: Implicits.Repeater[T, V],
               whitespace: Ctx[_] => Parsed[Unit]): Parsed[V] = rep(sep=null)
    def rep[V](min: Int = 0,
               max: Int = Int.MaxValue,
               exactly: Int = -1,
               sep: => Parsed[_] = null)
              (implicit repeater: Implicits.Repeater[T, V],
               whitespace: Ctx[_] => Parsed[Unit]): Parsed[V] = {


      val acc = repeater.initial
      val actualMin = if(exactly == -1) min else exactly
      val actualMax = if(exactly == -1) max else exactly
      def end(successIndex: Int, failureIndex: Int, count: Int) = {
        if (count < actualMin) ctx.prepareFailure(failureIndex)
        else ctx.prepareSuccess(repeater.result(acc), successIndex)
      }
      @tailrec def rec(startIndex: Int, count: Int, precut: Boolean): Parsed[V] = {
        whitespace(ctx) match {
          case f: Parsed.Failure => f
          case s: Parsed.Success[_] =>
            if (count == 0 && actualMax == 0) ctx.prepareSuccess(repeater.result(acc), startIndex)
            else conv(parse0) match {
              case f: Parsed.Failure =>

                if (f.cut | precut) f
                else end(startIndex, startIndex, count)


              case s: Parsed.Success[T] =>
                val beforeSepIndex = ctx.success.index
                repeater.accumulate(s.value, acc)
                if (count + 1 == actualMax) end(beforeSepIndex, beforeSepIndex, count + 1)
                else sep match {
                  case null => rec(beforeSepIndex, count + 1, false)
                  case s: Parsed.Success[_] => rec(beforeSepIndex, count + 1, s.cut)
                  case f: Parsed.Failure =>
                    if (f.cut) ctx.prepareFailure(beforeSepIndex)
                    else end(beforeSepIndex, beforeSepIndex, count + 1)
                }
            }
        }
      }
      val res = rec(ctx.success.index, 0, false)

      res
    }

    def literalize(s: IndexedSeq[Char], unicode: Boolean = true) = {
      val sb = new StringBuilder
      sb.append('"')
      var i = 0
      val len = s.length
      while (i < len) {
        (s(i): @switch) match {
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

      sb.result()
    }

    def log(implicit name: sourcecode.Name): Parsed[T] = {
      if (ctx.logDepth == -1) conv(parse0)
      else{
        val msg = name.value
        val output = println(_: String)
        val indent = "  " * ctx.logDepth

        output(s"$indent+$msg:${ctx.success.index}")
        val depth = ctx.logDepth
        ctx.logDepth += 1
        val res = conv(parse0)
        ctx.logDepth = depth
        val strRes = res match{
          case s: Parsed.Success[T] =>
            s"Success(${literalize(ctx.input.slice(s.index, s.index + 20))}${if (s.cut) ", cut" else ""})"
          case f: Parsed.Failure =>
//            val stack = Failure.filterFullStack(f.fullStack)
//            val trace = Failure.formatStackTrace(
//              stack.reverse,
//              f.input,
//              index,
//              Failure.formatParser(f.lastParser, f.input, f.index)
//            )
            val trace = ""
            s"Failure($trace${if (f.cut) ", cut" else ""})"
        }
        output(s"$indent-$msg:${res.index}:$strRes")
//        output(s"$indent-$msg:${repr.prettyIndex(cfg.input, index)}:$strRes")
        res
      }
    }

    def ! : Parsed[String] = macro captureMacro[S, T]

    def unary_! : Parsed[Unit] = {
      val startPos = ctx.success.index
      conv(parse0) match{
        case f: Parsed.Failure => ctx.freshSuccess((), startPos)
        case s: Parsed.Success[_] => ctx.prepareFailure(startPos)
      }
    }

    def ?[V](implicit optioner: Implicits.Optioner[T, V]): Parsed[V] = {
      val startPos = ctx.success.index

      conv(parse0) match{
        case f: Parsed.Failure =>
          if (f.cut) f
          else ctx.freshSuccess(optioner.none, startPos)

        case s: Parsed.Success[T] => ctx.prepareSuccess(optioner.some(s.value))
      }
    }


    def filter(f: T => Boolean): Parsed[T] = {
      conv(parse0) match{
        case f: Parsed.Failure => f
        case s: Parsed.Success[T] =>
          if (f(s.value)) s
          else ctx.freshFailure()
      }
    }
  }

  def &(parse: => Parsed[_])(implicit ctx: Ctx[_]): Parsed[Unit] = {
    val startPos = ctx.success.index
    parse match{
      case f: Parsed.Failure => f
      case s: Parsed.Success[_] => ctx.prepareSuccess((), startPos)
    }
  }

  def End(implicit ctx: Ctx[_]): Parsed[Unit] = {
    if (ctx.success.index == ctx.input.length) ctx.freshSuccess(())
    else ctx.freshFailure()
  }

  def Start(implicit ctx: Ctx[_]): Parsed[Unit] = {
    if (ctx.success.index == 0) ctx.freshSuccess(())
    else ctx.freshFailure()

  }

  def Pass(implicit ctx: Ctx[_]): Parsed[Unit] = ctx.freshSuccess(())

  def Fail(implicit ctx: Ctx[_]): Parsed[Nothing] = ctx.freshFailure()

  def Index(implicit ctx: Ctx[_]): Parsed[Int] = ctx.freshSuccess(ctx.success.index)

  def AnyChar(implicit ctx: Ctx[_]): Parsed[Unit] = CharPred(_ => true)
  def CharPred(p: Char => Boolean)(implicit ctx: Ctx[_]): Parsed[Unit] = {
    if (!(ctx.success.index < ctx.input.length && p(ctx.input(ctx.success.index)))) ctx.freshFailure()
    else ctx.freshSuccess((), ctx.success.index + 1)
  }
  def CharsWhile(p: Char => Boolean, min: Int = 1)(implicit ctx: Ctx[_]) = {
    def currentCharMatches = {
      val index = ctx.success.index
      val input = ctx.input
      index < input.length && p(input(index))
    }

    if (!currentCharMatches)  {
      ctx.isSuccess = false
      if (min == 0) ctx.freshSuccess(()) else ctx.failure
    }else{
      val start = ctx.success.index
      while(currentCharMatches) ctx.success.index += 1
      if (ctx.success.index - start >= min) ctx.freshSuccess(()) else ctx.failure
    }
  }


  implicit def parseInputCtx(s: String): Ctx[_] = Ctx(s)
}

class Ctx[+_](val input: String,
              val stack: mutable.Buffer[String],
              val success: Parsed.Success[_],
              val failure: Parsed.Failure,
              var isSuccess: Boolean,
              var logDepth: Int){

  def freshSuccess[T](value: T, index: Int = success.index) = prepareSuccess(value, index, cut = false)
  def prepareSuccess[T](value: T, index: Int = success.index, cut: Boolean = success.cut): Parsed.Success[T] = {
    val res = success.asInstanceOf[Parsed.Success[T]]
    isSuccess = true
    res.value0 = value
    res.index = index
    res.cut = cut
    res
  }
  def freshFailure(startPos: Int = success.index): Parsed.Failure = {
    prepareFailure(startPos, cut = false)
  }

  def prepareFailure(index: Int, cut: Boolean = failure.cut): Parsed.Failure = {
    isSuccess = false
    failure.stack = Nil
    failure.index = index
    failure.cut = cut
    failure
  }
}
object Ctx{
  def apply(input: String) = new Ctx(
    input = input,
    stack = mutable.Buffer.empty,
    success = new Parsed.Success,
    failure = new Parsed.Failure,
    isSuccess = true,
    logDepth = 0
  )
}

abstract class Parsed[+T](val isSuccess: Boolean){
  var cut: Boolean = false
  var index: Int = 0
  def map[V](f: T => V): Parsed[V]
  def flatMap[V](f: T => Parsed[V]): Parsed[V]
  def get: Parsed.Success[T]
}

object Parsed{
  object Success{
    def unapply[T](x: Parsed[T]): Option[(T, Int)] = x match{
      case s: Success[T] => Some((s.value, s.index))
      case f: Failure => None
    }
  }
  object Failure{
    def unapply[T](x: Parsed[T]): Option[(Unit, Int, Unit)] = x match{
      case s: Failure => Some(((), s.index, ()))
      case f: Success[T] => None
    }
  }
  class Success[+T] extends Parsed[T](true){
    def get = this
    var value0: Any = null
    def value = value0.asInstanceOf[T]
    def map[V](f: T => V) = {
      val this2 = this.asInstanceOf[Success[V]]
      this2.value0 = f(value)
      this2
    }
    def flatMap[V](f: T => Parsed[V]): Parsed[V] = {
      f(value) match{
        case f: Failure => f
        case s: Success[V] => s
      }
    }
    override def toString() = s"Parsed.Success($value)"
  }
  class Failure extends Parsed[Nothing](false){
    def get = throw new Exception("Parse Error at " + index + ":\n" + stack.mkString("\n"))
    var stack = List.empty[String]
    override def toString() = s"Parsed.Failure($index)"
    def map[V](f: Nothing => V) = this
    def flatMap[V](f: Nothing => Parsed[V]): Parsed[V] = this
  }
}