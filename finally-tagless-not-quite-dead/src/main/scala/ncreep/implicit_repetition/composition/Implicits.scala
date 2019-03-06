package ncreep.implicit_repetition.composition

import ncreep.implicit_repetition._
import ncreep.implicit_repetition.composition.Runner._
import scala.language.higherKinds

object Runner {
  def doStuff[F[_] : Program]: F[Unit] = {
    val program = Program[F]
    import program._

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

class Program[F[_]](implicit
                    val serviceA: ServiceA[F],
                    val serviceB: ServiceB[F],
                    val serviceC: ServiceC[F],
                    val serviceD: ServiceD[F])

object Program {
  def apply[F[_]](implicit program: Program[F]): Program[F] = program

  implicit def program[F[_]](implicit
                             serviceA: ServiceA[F],
                             serviceB: ServiceB[F],
                             serviceC: ServiceC[F],
                             serviceD: ServiceD[F]): Program[F] = new Program[F]
}
