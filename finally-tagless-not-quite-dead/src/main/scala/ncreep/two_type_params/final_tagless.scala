package ncreep.two_type_params

import ncreep.MonadReader.Syntax
import ncreep.ZIO._
import ncreep.two_type_params.Console.Live
import ncreep.{MonadReader, ReaderT, Writer, ZIO}
import scala.language.higherKinds

class Programs[F[- _, _] : MonadReader] {
  val console = Console[F]
  val logging = Logging[F]
  val persistence = Persistence[F]

  import console._
  import logging._
  import persistence._

  // Inferred type:
  // F[Console[F], String]
  val simpleInteraction =
    for {
      _ <- putStrLn("Good morning, what is your name?")
      name <- getStrLn
      _ <- putStrLn(s"Good to meet you, $name!")
    } yield name

  // Inferred type:
  // F[Console[F] with Logging[F] with Persistence[F], Unit]
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
  type ProgramEnv[F[_, _]] = Console[F] with Logging[F] with Persistence[F]
  type Program[F[_, _], A] = F[ProgramEnv[F], A]

  val programs = new Programs[ZIO]

  val simpleProgram = programs.simpleInteraction.provide(Console.withService(Live))

  val complexProgram: Program[ZIO, Unit] = programs.complexInteraction
}

object Testing {

  type TestingEnv[-R, A] = ReaderT[Writer, R, A]

  object TestingConsole extends Console.Service[TestingEnv] {
    def putStrLn(line: String): TestingEnv[Any, Unit] =
      ReaderT.lift(Writer.tell(line))

    val getStrLn: TestingEnv[Any, String] =
      ReaderT.pure("Anonymous")
  }

  val programs = new Programs[TestingEnv]

  val (log, _) =
    programs.simpleInteraction.provide(Console.withService(TestingConsole)).write

  // log == List(Good morning, what is your name?, Good to meet you, Anonymous!)
}

trait Console[F[_, _]] {
  def console: Console.Service[F]
}

object Console {
  trait Service[F[_, _]] {
    def putStrLn(line: String): F[Any, Unit]

    val getStrLn: F[Any, String]
  }

  def withService[F[_, _]](service: Service[F]): Console[F] = new Console[F] {
    def console: Service[F] = service
  }

  def apply[F[- _, _]](implicit reader: MonadReader[F]): Syntax[F] = new Syntax

  class Syntax[F[- _, _]](implicit reader: MonadReader[F]) {

    def putStrLn(line: String): F[Console[F], Unit] =
      reader.accessM(_.console.putStrLn(line))

    val getStrLn: F[Console[F], String] =
      reader.accessM(_.console.getStrLn)
  }

  trait Live extends Console.Service[ZIO] {
    def putStrLn(line: String): IO[Unit] = ???

    val getStrLn: IO[String] = ???
  }

  object Live extends Live
}

trait Logging[F[_, _]] {
  def logging: Logging.Service[F]
}

object Logging {
  trait Service[F[_, _]] {
    def debug(message: String): F[Any, Unit]
  }

  def withService[F[_, _]](service: Service[F]): Logging[F] = new Logging[F] {
    def logging: Service[F] = service
  }

  def apply[F[- _, _]](implicit reader: MonadReader[F]): Syntax[F] = new Syntax

  class Syntax[F[- _, _]](implicit reader: MonadReader[F]) {

    def debug(message: String): F[Logging[F], Unit] =
      reader.accessM(_.logging.debug(message))
  }
}

trait Persistence[F[_, _]] {
  def persistence: Persistence.Service[F]
}

object Persistence {
  trait Service[F[_, _]] {
    def savePreferences(name: String): F[Any, Unit]
  }

  def withService[F[_, _]](service: Service[F]): Persistence[F] = new Persistence[F] {
    def persistence: Service[F] = service
  }

  def apply[F[- _, _]](implicit reader: MonadReader[F]): Syntax[F] = new Syntax

  class Syntax[F[- _, _]](implicit reader: MonadReader[F]) {

    def savePreferences(name: String): F[Persistence[F], Unit] =
      reader.accessM(_.persistence.savePreferences(name))
  }
}

