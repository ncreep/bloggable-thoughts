package ncreep

import scalaz.Monad

/** A dummy type to stand for the ZIO environment implementation. Not including the third type-parameter (for error handling),
  * it doesn't play any role in the current exploration.
  */
sealed trait ZIO[-R, +A] {
  def provide(environment: R): ZIO[Any, A] = ???
}

object ZIO {
  type IO[+A] = ZIO[Any, A]

  implicit def reader: MonadReader[ZIO] = ???

  implicit val monad: Monad[IO] = ???
}
