package ncreep.one_type_param_currying_kind_projector

import ncreep.MonadReader.Syntax
import ncreep.ZIO._
import ncreep.one_type_param_currying_kind_projector.Console.Live
import ncreep.{MonadReader, ZIO}
import scala.language.higherKinds

class Programs[F[- _, _] : MonadReader] {
  val console = Console[F]
  val logging = Logging[F]
  val persistence = Persistence[F]

  import console._
  import logging._
  import persistence._

  val simpleInteraction =
    for {
      _ <- putStrLn("Good morning, what is your name?")
      name <- getStrLn
      _ <- putStrLn(s"Good to meet you, $name!")
    } yield name

  // Inferred type:
  //  F[Console[({ type Λ$[β] = F[Any, β] })#Λ$] with
  //    Logging[({ type Λ$[β] = F[Any, β] })#Λ$] with
  //    Persistence[({ type Λ$[β] = F[Any, β] })#Λ$], Unit]
  val complexInteraction =
    for {
      _ <- putStrLn("Good morning, what is your name?")
      name <- getStrLn
      _ <- savePreferences(name)
      _ <- debug("Saved $name to configuration")
      _ <- putStrLn(s"Good to meet you, $name!")
    } yield ()
}

object Runner {
  type ProgramEnv[F[_]] = Console[F] with Logging[F] with Persistence[F]
  type Program[F[_, _], A] = F[ProgramEnv[F[Any, ?]], A]

  val programs = new Programs[ZIO]

  val simpleProgram = programs.simpleInteraction.provide(Console.withService(Live))

  val complexProgram: Program[ZIO, Unit] = programs.complexInteraction
}

trait Console[F[_]] {
  def console: Console.Service[F]
}

object Console {
  trait Service[F[_]] {
    def putStrLn(line: String): F[Unit]

    val getStrLn: F[String]
  }

  def withService[F[_]](service: Service[F]): Console[F] = new Console[F] {
    def console: Service[F] = service
  }

  def apply[F[- _, _]](implicit reader: MonadReader[F]): Syntax[F] = new Syntax

  class Syntax[F[- _, _]](implicit reader: MonadReader[F]) {

    def putStrLn(line: String): F[Console[F[Any, ?]], Unit] =
      reader.accessM(_.console.putStrLn(line))

    val getStrLn: F[Console[F[Any, ?]], String] =
      reader.accessM(_.console.getStrLn)
  }

  trait Live extends Console.Service[IO] {
    def putStrLn(line: String): IO[Unit] = ???

    val getStrLn: IO[String] = ???
  }

  object Live extends Live
}

trait Logging[F[_]] {
  def logging: Logging.Service[F]
}

object Logging {
  trait Service[F[_]] {
    def debug(message: String): F[Unit]
  }

  def withService[F[_]](service: Service[F]): Logging[F] = new Logging[F] {
    def logging: Service[F] = service
  }

  def apply[F[- _, _]](implicit reader: MonadReader[F]): Syntax[F] = new Syntax

  class Syntax[F[- _, _]](implicit reader: MonadReader[F]) {

    def debug(message: String): F[Logging[F[Any, ?]], Unit] =
      reader.accessM(_.logging.debug(message))
  }
}

trait Persistence[F[_]] {
  def persistence: Persistence.Service[F]
}

object Persistence {
  trait Service[F[_]] {
    def savePreferences(name: String): F[Unit]
  }

  def withService[F[_]](service: Service[F]): Persistence[F] = new Persistence[F] {
    def persistence: Service[F] = service
  }

  def apply[F[- _, _]](implicit reader: MonadReader[F]): Syntax[F] = new Syntax

  class Syntax[F[- _, _]](implicit reader: MonadReader[F]) {

    def savePreferences(name: String): F[Persistence[F[Any, ?]], Unit] =
      reader.accessM(_.persistence.savePreferences(name))
  }
}

