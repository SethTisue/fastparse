package fastparse

/**
 * Basically all stolen verbatim from
 *
 * https://apocalisp.wordpress.com/2010/07/06/
 * type-level-programming-in-scala-part-6a-heterogeneous-list%C2%A0basics/
 *
 */
object Variadic {

  trait Fold[-Elem, Value] {
    type Apply[N <: Elem, Acc <: Value] <: Value

    def apply[N <: Elem, Acc <: Value](n: N, acc: Acc): Apply[N, Acc]
  }

  sealed trait HList {
    type Foldr[Value, F <: Fold[Any, Value], I <: Value] <: Value

    def foldr[Value, F <: Fold[Any, Value], I <: Value](f: F, i: I): Foldr[Value, F, I]
  }

  final case class HCons[H, T <: HList](head: H, tail: T) extends HList {
    def ::[T](v: T) = HCons(v, this)

    type Foldr[Value, F <: Fold[Any, Value], I <: Value] =
    F#Apply[H, tail.Foldr[Value, F, I]]

    def foldr[Value, F <: Fold[Any, Value], I <: Value](f: F, i: I): Foldr[Value, F, I] =
      f(head, tail.foldr[Value, F, I](f, i))
  }

  case object HNil extends HNil
  sealed trait HNil extends HList {
    def ::[T](v: T) = HCons(v, this)

    type Foldr[Value, F <: Fold[Any, Value], I <: Value] = I

    def foldr[Value, F <: Fold[Any, Value], I <: Value](f: F, i: I) = i
  }

  type :::[A <: HList, B <: HList] = A#Foldr[HList, AppHCons.type, B]

  object AppHCons extends Fold[Any, HList] {
    type Apply[N <: Any, H <: HList] = N :: H
    def apply[A,B <: HList](a: A, b: B) = HCons(a, b)
  }

  implicit class HListOps[B <: HList](b: B){
    def :::[A <: HList](a: A): A#Foldr[HList, AppHCons.type, B] =
      a.foldr[HList, AppHCons.type, B](AppHCons, b)
  }

  type ::[H, T <: HList] = HCons[H, T]
  val :: = HCons


  type H1[T1] = T1 :: HNil
  type H2[T1, T2] = T2 :: H1[T1]
  type H3[T1, T2, T3] = T3 :: H2[T1, T2]
  implicit class Mapper0(t: HNil){
    def map[V](f: => V) = f
    def flatten = ()
  }
  implicit class Mapper1[T1](t: T1 :: HNil){
    def map[V](f: T1 => V) = f(t.head)
    def flatten = t.head
  }
  implicit class Mapper2[T1, T2](t: T1 :: T2 :: HNil){
    def map[V](f: (T1, T2) => V) = f.tupled(flatten)
    def flatten = (t.head, t.tail.head)
  }
  implicit class Mapper3[T1, T2, T3](t: T1 :: T2 :: T3 :: HNil){
    def map[V](f: (T1, T2, T3) => V) = f.tupled(flatten)
    def flatten = { val t1 = t.tail; (t.head, t1.head, t1.tail.head) }
  }
  implicit class Mapper4[T1, T2, T3, T4](t: T1 :: T2 :: T3 :: T4 :: HNil){
    def map[V](f: (T1, T2, T3, T4) => V) = f.tupled(flatten)
    def flatten = { val t1 = t.tail; val t2 = t1.tail; (t.head, t1.head, t2.head, t2.tail.head) }
  }
  implicit class Mapper5[T1, T2, T3, T4, T5](t: T1 :: T2 :: T3 :: T4 :: T5 :: HNil){
    def map[V](f: (T1, T2, T3, T4, T5) => V) = f.tupled(flatten)
    def flatten = { val t1 = t.tail; val t2 = t1.tail; val t3 = t2.tail; (t.head, t1.head, t2.head, t3.head, t3.tail.head) }
  }
  implicit class Mapper6[T1, T2, T3, T4, T5, T6](t: T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: HNil){
    def map[V](f: (T1, T2, T3, T4, T5, T6) => V) = f.tupled(flatten)
    def flatten = { val t1 = t.tail; val t2 = t1.tail; val t3 = t2.tail; val t4 = t3.tail; (t.head, t1.head, t2.head, t3.head, t4.head, t4.tail.head) }
  }
  implicit class Mapper7[T1, T2, T3, T4, T5, T6, T7](t: T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: HNil){
    def map[V](f: (T1, T2, T3, T4, T5, T6, T7) => V) = f.tupled(flatten)
    def flatten = { val t1 = t.tail; val t2 = t1.tail; val t3 = t2.tail; val t4 = t3.tail; val t5 = t4.tail; (t.head, t1.head, t2.head, t3.head, t4.head, t5.head, t5.tail.head) }
  }
  implicit class Mapper8[T1, T2, T3, T4, T5, T6, T7, T8](t: T1 :: T2 :: T3 :: T4 :: T5 :: T6 :: T7 :: T8 :: HNil){
    def map[V](f: (T1, T2, T3, T4, T5, T6, T7, T8) => V) = f.tupled(flatten)
    def flatten = { val t1 = t.tail; val t2 = t1.tail; val t3 = t2.tail; val t4 = t3.tail; val t5 = t4.tail; val t6 = t5.tail; (t.head, t1.head, t2.head, t3.head, t4.head, t5.head, t6.head, t6.tail.head) }
  }
}

object Main {
  import Variadic._


  def main(args: Array[String]): Unit = {
    val a = 1 :: "lol" :: HNil
    val b = 2 :: 'X' :: HNil
//    val c = append(a, b)
    println(a)
    println(b)
    println(a.tail)
    println(a ::: b)
    println(1 :: HNil map (x => x))
    println((a ::: b).map(_+_+_+_))
  }
}
