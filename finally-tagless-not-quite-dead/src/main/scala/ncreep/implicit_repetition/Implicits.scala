package ncreep.implicit_repetition

import scala.language.higherKinds

object Implicits {
  def doStuff[F[_] : ServiceA : ServiceB : ServiceC : ServiceD]: F[Unit] = ???
}
