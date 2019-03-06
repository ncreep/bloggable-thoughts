package ncreep.reader_t

import ncreep.{ReaderT, Writer}
import ncreep.ZIO._
import ncreep.reader_t.Console.Live
import scalaz.Monad
import scala.language.higherKinds

class Programs[F[_] : Monad] {
  val console = Console[F]
  val logging = Logging[F]
  val persistence = Persistence[F]

  import console._
  import logging._
  import persistence._

  // Inferred type:
  // ReaderT[F, Console[F], String]
  val simpleInteraction =
    for {
      _ <- putStrLn("Good morning, what is your name?")
      name <- getStrLn
      _ <- putStrLn(s"Good to meet you, $name!")
    } yield name

  // Inferred type:
  // ReaderT[F, Console[F] with Logging[F] with Persistence[F], Unit]
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
  type Program[F[_], A] = ReaderT[F, ProgramEnv[F], A]

  val programs = new Programs[IO]

  val simpleProgram: IO[String] = programs.simpleInteraction.provide(Console.withService(Live))

  val complexProgram: Program[IO, Unit] = programs.complexInteraction
}

object Testing {
  type TestingEnv[A] = Writer[A]

  object TestingConsole extends Console.Service[TestingEnv] {
    def putStrLn(line: String): TestingEnv[Unit] =
      Writer.tell(line)

    val getStrLn: TestingEnv[String] =
      Writer.pure("Anonymous")
  }

  val programs = new Programs[TestingEnv]

  val (log, _) =
    programs.simpleInteraction.provide(Console.withService(TestingConsole)).write

  // log == List(Good morning, what is your name?, Good to meet you, Anonymous!)
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

  def apply[F[_]]: Syntax[F] = new Syntax

  class Syntax[F[_]] {
    def putStrLn(line: String): ReaderT[F, Console[F], Unit] =
      ReaderT(_.console.putStrLn(line))

    val getStrLn: ReaderT[F, Console[F], String] =
      ReaderT(_.console.getStrLn)
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

  def apply[F[_]]: Syntax[F] = new Syntax

  class Syntax[F[_]] {
    def debug(message: String): ReaderT[F, Logging[F], Unit] =
      ReaderT(_.logging.debug(message))
  }
}

trait Persistence[F[_]] {
  def persistence: Persistence.Service[F]
}

object Persistence {
  trait Service[F[_]] {
    def savePreferences(name: String): F[Unit]
  }

  def apply[F[_]]: Syntax[F] = new Syntax

  class Syntax[F[_]] {
    def savePreferences(name: String): ReaderT[F, Persistence[F], Unit] =
      ReaderT(_.persistence.savePreferences(name))
  }
}


