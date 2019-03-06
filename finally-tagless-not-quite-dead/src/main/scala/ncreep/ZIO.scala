package ncreep

import scalaz.Monad

sealed trait ZIO[-R, +A] {
  def provide(environment: R): ZIO[Any, A] = ???
}

object ZIO {
  type IO[+A] = ZIO[Any, A]

  implicit def reader: MonadReader[ZIO] = ???

  implicit val monad: Monad[IO] = ???
}
