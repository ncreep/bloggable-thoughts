package ncreep.implicit_repetition.inheritance

import ncreep.implicit_repetition._
import ncreep.implicit_repetition.inheritance.Runner._
import scala.language.higherKinds

object Runner {
  def doStuff[F[_] : Program]: F[Unit] = {
    implicitly[ServiceA[F]]
    implicitly[ServiceB[F]]
    implicitly[ServiceC[F]]
    implicitly[ServiceD[F]]

    ???
  }
}

object Implicits {
  implicit val serviceA: ServiceA[Option] = ???
  implicit val serviceB: ServiceB[Option] = ???
  implicit val serviceC: ServiceC[Option] = ???
  implicit val serviceD: ServiceD[Option] = ???

  val result: Option[Unit] = doStuff
}

trait Program[F[_]] extends ServiceA[F] with ServiceB[F] with ServiceC[F] with ServiceD[F]

object Program {
  implicit def program[F[_]](implicit
                             serviceA: ServiceA[F],
                             serviceB: ServiceB[F],
                             serviceC: ServiceC[F],
                             serviceD: ServiceD[F]): Program[F] = new Program[F] {
    def doA(i: Int): F[String] = serviceA.doA(i)

    def doB(s: String): F[Int] = serviceB.doB(s)

    def doC: F[Boolean] = serviceC.doC

    def doD(b: Boolean): F[Unit] = serviceD.doD(b)
  }
}

