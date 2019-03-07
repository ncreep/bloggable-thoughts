package ncreep

import scalaz.Monad
import scalaz.syntax.monad._
import scala.language.higherKinds

trait MonadReader[F[- _, _]] {
  def flatMap[R1 <: R, R, A, B](fa: F[R, A])(f: A => F[R1, B]): F[R1, B]

  def map[R, A, B](fa: F[R, A])(f: A => B): F[R, B]

  def pure[A](a: A): F[Any, A]

  def accessM[R, A](f: R => F[R, A]): F[R, A]

  def provide[R, A](fa: F[R, A])(environment: R): F[Any, A]
}

object MonadReader {
  implicit class Syntax[F[- _, _], R, A](self: F[R, A])(implicit reader: MonadReader[F]) {
    def flatMap[R1 <: R, B](f: A => F[R1, B]): F[R1, B] = reader.flatMap(self)(f)

    def map[B](f: A => B): F[R, B] = reader.map(self)(f)

    def provide(environment: R): F[Any, A] = reader.provide(self)(environment)
  }
}

case class ReaderT[F[_], -R, A](provide: R => F[A]) { self =>
  def map[R1 <: R, B](f: A => B)
                     (implicit monad: Monad[F]): ReaderT[F, R1, B] = flatMap(a => ReaderT.pure(f(a)))

  def flatMap[R1 <: R, B](f: A => ReaderT[F, R1, B])
                         (implicit monad: Monad[F]): ReaderT[F, R1, B] =
    ReaderT[F, R1, B](r => self.provide(r).flatMap(a => f(a).provide(r)))
}

object ReaderT {
  def pure[F[_], A](a: => A)
                   (implicit monad: Monad[F]): ReaderT[F, Any, A] = ReaderT(_ => a.pure)

  def lift[F[_], A](fa: F[A]): ReaderT[F, Any, A] = ReaderT(_ => fa)

  implicit def monad[F[_] : Monad, R]: Monad[ReaderT[F, R, ?]] =
    new Monad[ReaderT[F, R, ?]] {
      def bind[A, B](fa: ReaderT[F, R, A])
                    (f: A => ReaderT[F, R, B]): ReaderT[F, R, B] =
        fa.flatMap(f)

      def point[A](a: => A): ReaderT[F, R, A] = ReaderT.pure(a)
    }

  implicit def monadReader[F[_] : Monad]: MonadReader[ReaderT[F, -?, ?]] = new MonadReader[ReaderT[F, -?, ?]] {
    def flatMap[R1 <: R, R, A, B](fa: ReaderT[F, R, A])(f: A => ReaderT[F, R1, B]): ReaderT[F, R1, B] =
      fa.flatMap(f)

    def map[R, A, B](fa: ReaderT[F, R, A])(f: A => B): ReaderT[F, R, B] =
      fa.map(f)

    def pure[A](a: A): ReaderT[F, Any, A] =
      ReaderT.pure(a)

    def accessM[R, A](f: R => ReaderT[F, R, A]): ReaderT[F, R, A] = ReaderT((r: R) => r.pure).flatMap(f)

    def provide[R, A](fa: ReaderT[F, R, A])(environment: R): ReaderT[F, Any, A] =
      lift(fa.provide(environment))
  }
}

// A highly under-performant writer - DO NOT USE IN PRODUCTION
case class Writer[+A](write: (List[String], A)) {
  self =>
  def map[B](f: A => B): Writer[B] = flatMap(a => Writer.pure(f(a)))

  def flatMap[B](f: A => Writer[B]): Writer[B] =
    Writer[B] {
      val (ls1, a) = write
      val (ls2, b) = f(a).write

      (ls1 ++ ls2, b)
    }
}

object Writer {
  def pure[A](a: => A): Writer[A] = Writer((Nil, a))

  def tell(str: String): Writer[Unit] = Writer((List(str), ()))

  implicit val monad: Monad[Writer] = new Monad[Writer] {
    def bind[A, B](fa: Writer[A])(f: A => Writer[B]): Writer[B] = fa.flatMap(f)

    def point[A](a: => A): Writer[A] = Writer.pure(a)
  }
}

