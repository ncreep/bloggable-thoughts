package ncreep.implicit_repetition

import scala.language.higherKinds

trait ServiceA[F[_]] {def doA(i: Int): F[String]}
trait ServiceB[F[_]] {def doB(s: String): F[Int]}
trait ServiceC[F[_]] {def doC: F[Boolean]}
trait ServiceD[F[_]] {def doD(b: Boolean): F[Unit]}
