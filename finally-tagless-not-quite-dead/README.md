# Finally Tagless - Not Quite Dead Yet
In a recent [blog post](http://degoes.net/articles/zio-environment) and accompanying [talk](https://skillsmatter.com/skillscasts/13247-scala-matters), John De Goes proposed an alternative to the currently popular "finally tagless" approach to writing (purely) functional code - ZIO environment. In the talk, ZIO environment is designated to be "the death of finally tagless". In this post I would like to discuss some of the points that were raised against the finally tagless approach, and show how we can apply some of the ideas used by ZIO environment to improve various aspects of the finally tagless encoding.

For a brief background on finally tagless, as well as the actually proposed alternative, please consult the links above, both are highly recommended. This post will not make much sense without reading/watching them first.

John's post raises a number of points about the shortcomings of the finally tagless approach. Some of them I find to be debatable, but eventually they boil down to a matter of opinion. I will defer their discussion to the end of this entry. What I would like to start out with here are what I find to be the more technical points against finally-tagless. Let's begin with the tedium of repeating implicit constraints.

## Implicit Repetition

We start with the standard way of defining the finally tagless building blocks, the so-called algebras:
```scala
trait ServiceA[F[_]] { def doA(i: Int): F[String] }
trait ServiceB[F[_]] { def doB(s: String): F[Int] }
trait ServiceC[F[_]] { def doC: F[Boolean] }
trait ServiceD[F[_]] { def doD(b: Boolean): F[Unit] }
```

This defines a bunch of services, each one supporting some actions. One way to think about these traits are as capabilities that we want to endow our abstract context `F[_]` with. So, if we want to postulate that the context our code lives in can perform these actions, we'll have to write something like the following:
```scala
def doStuff[F[_]: ServiceA: ServiceB: ServiceC: ServiceD]: F[Unit] = ???
```

Now, as John points out, if we consistently apply the finally tagless approach, we'll have to repeat these context bounds all over the place. In an ideal world, we would be able write something like:
```scala
type Program[F[_]] = (implicit ServiceA[F], ServiceB[F], ServiceC[F], ServiceD[F]) 

def doStuff[F[_]: Program] = ???
```

Unfortunately, in the current version of Scala, there isn't any way to abstract over implicit arguments. And so the snippet above cannot represent actual code.

What we can do, is to offset the repetition by investing in some boilerplate. Something similar to the code above can be achieved as follows:
```scala
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
```
(Note that the full code for the examples in this post can be found under the [`src`](src/main/scala/ncreep) folder of the repository.)

Now, at the use-sites we can do the following:
```scala
def doStuff[F[_] : Program]: F[Unit] = {
  val program = Program[F]
  import program._

  implicitly[ServiceA[F]]
  implicitly[ServiceB[F]]
  implicitly[ServiceC[F]]
  implicitly[ServiceD[F]]

  ???
}
```

This is far from ideal, but it can save some repetition, and the boilerplate could possibly be reduced with some magical macro.

There's a point to be made here, that using implicits to pass around our algebras is an abuse of the concept of a typeclass, as these traits will not, in general, be equipped with any useful laws. While this point of view has its merits in a language with proper typeclasses (like Haskell), in Scala this point becomes somewhat moot. Since anything in Scala can be implicit, one might claim that we are just passing around values, rather than typeclass instances (the fact that they are parameterized like a typeclass is just a coincidence). Given that using implicits for dependency injection in Scala is not all that uncommon, we can claim that we are just injecting values into our programs. Typeclasses and laws have nothing to do with it. Though as we'll see, dependency injection is going to be a central theme in what follows below.

Whether or not you accept using implicits in such a way, and even if you accept the approach described above as a solution to the problem of repetition (which you probably shouldn't), it doesn't in any way address John's next point.

## Lack of Type Inference

Since we are using implicits to gain access to the capabilities that we require, there's no way for the compiler to actually infer the type signatures of the code that we are writing. We must explicitly write out the implicits for the compiler to be happy.

To make the problem more concrete, I'll use the example that John's uses in his post. Given that we have the following console capability:
```scala
trait Console[F[_]] {
  def putStrLn(line: String): F[Unit]

  val getStrLn: F[String]
}
```

There's no straightforward way to write something like the following:
```
val program = for {
  _ <- putStrLn("Good morning, what is your name?")
  name <- getStrLn
  _ <- putStrLn(s"Good to meet you, $name!")
} yield name
```

The compiler has no way of inferring the type of `program`, since it has no way of figuring out that we need an implicit `Console` in scope. On the other hand, ZIO environment solves this problem, making analogous code written in the ZIO approach fully inferable, without requiring us to explicitly pass around `Console` values. 

To adopt the ZIO approach, we'll have to drop the ability to abstract over our context, and fully commit to the ZIO datatype. Can't we have the best of all worlds? That is, full type-inference, while maintaining abstraction, and without tediously passing around capabilities?

Obviously, I wouldn't be asking this question, or even bothering to write this post, if the answer wasn't "yes". To see why this is so, we need examine what exactly ZIO does to achieve these results. 

One important thing to note about the ZIO approach, is that nowhere do we use any of the features that ZIO is famous for; there are no fibers, brackets, or anything similar in sight. The only thing we require of ZIO in this context is the ability to provide computations with an environment value (in our case `Console`) as well as a `Monad` instance, to allow chaining actions. If you're into functional programming (which given that you've read this far, you probably are), this might sound familiar. These are the capabilities that are abstractly defined by [`MonadReader`](http://hackage.haskell.org/package/mtl-2.2.2/docs/Control-Monad-Reader-Class.html). As we want to generalize what ZIO is doing and use a generic context, we'll need to imbue our `F` with the `MonadReader` capabilities. That's exactly what the `ReaderT` monad transformer lets us do. We'll use the following the definition of `ReaderT`:
```scala
case class ReaderT[F[_], -R, A](provide: R => F[A])
```

Note that this differs from standard definitions of `ReaderT` (like the ones you'll find in Scalaz and Cats) in that the `R` type-parameter is contravariant. This turns out to be an important ingredient in the quest for type inference.

In preparation for what's to come, we'll adopt the module convention that John uses, so that we have this definition of `Console`:
```scala
trait Console[F[_]] {
  def console: Console.Service[F]
}

object Console {
  trait Service[F[_]] {
    def putStrLn(line: String): F[Unit]

    val getStrLn: F[String]
  }
}
```

At this point, in the ZIO approach we need to define some helper functions to make the usage of modules look nicer. We'll do something similar here:
```scala
// In the Console companion

def apply[F[_]]: Syntax[F] = new Syntax

class Syntax[F[_]] {
  def putStrLn(line: String): ReaderT[F, Console[F], Unit] =
    ReaderT(_.console.putStrLn(line))

  val getStrLn: ReaderT[F, Console[F], String] =
    ReaderT(_.console.getStrLn)
}
```

With this in place we can now write the following code:
```scala
class Programs[F[_]: Monad] {
  val console = Console[F]

  import console._

  val simpleInteraction =
    for {
      _ <- putStrLn("Good morning, what is your name?")
      name <- getStrLn
      _ <- putStrLn(s"Good to meet you, $name!")
    } yield name
}
```

Now the compiler is happy to infer `simpleInteraction` as `ReaderT[F, Console[F], String]`. Although we are not completely free from passing implicits, but now it's just the `Monad` constraint, which is a legitimate typeclass, and a standard one at that. While this requires somewhat more ceremony, we've regained our generic context, so we can run this computation in some `IO`-like container:
```scala
val live: Console[IO] = Console.withSerivce(Live)
val simpleProgram: IO[String] = programs.simpleInteraction.provide(live)
```
But we can also run tests in a controlled, pure environment, like a `Writer` instance that will collect data about the console interaction:
```scala
type TestingEnv[A] = Writer[A]

object TestingConsole extends Console.Service[TestingEnv] {
  def putStrLn(line: String): TestingEnv[Unit] =
    Writer.tell(line)

  val getStrLn: TestingEnv[String] =
    Writer.pure("Anonymous")
}

val programs = new Programs[TestingEnv]

val testProgram =
  programs.simpleInteraction.provide(Console.withService(TestingConsole))
  
val (log, _) = testProgram.write

// log == List(Good morning, what is your name?, Good to meet you, Anonymous!)
```

So with these two ingredients, `ReaderT` and `Monad`, we can imbue any generic type-constructor with the environmental capabilities of ZIO.

This approach scales just like the ZIO approach does. That's where the non-standard variance annotation on `ReaderT` comes into play. Given the appropriate definitions and imports for `Logging` and `Presistence`, we can write the following:
```scala
val complexInteraction =
  for {
    _ <- putStrLn("Good morning, what is your name?")
    name <- getStrLn
    _ <- savePreferences(name)
    _ <- debug("Saved $name to configuration")
    _ <- putStrLn(s"Good to meet you, $name!")
  } yield ()
```

This infers to `ReaderT[F, Console[F] with Logging[F] with Persistence[F], Unit]`.

So `ReaderT` can bring back most of the ergonomics that were introduced by ZIO environment back into the world of finally tagless.

At this point, someone, most notably John, might interject with the fact a `ReaderT` over some generic `F[_]` can be unacceptably slow. For more details, see John's [post on effect rotation](http://degoes.net/articles/rotating-effects). In certain circumstances this can definitely be a valid concern. Let's see how we can regain back the speed we lost.

## MonadReader - Take 2

The approach of "effect rotation" can be applied to our situation as well. All we need to do, is to directly lift the reader-like interface that ZIO provides into a proper typeclass, we'll call it `MonadReader`, just to confuse future generations:
```scala
trait MonadReader[F[-_, _]] {
  def flatMap[R1 <: R, R, A, B](fa: F[R, A])(f: A => F[R1, B]): F[R1, B]

  def map[R, A, B](fa: F[R, A])(f: A => B): F[R, B]

  def pure[A](a: A): F[Any, A]

  def accessM[R, A](f: R => F[R, A]): F[R, A]

  def provide[R, A](fa: F[R, A])(environment: R): F[Any, A]
}
```

Since this is a non-standard typeclass, I've inlined the definition of `Monad` into it. If we were to create a library around this, we'll have to rewrite the `Monad` hierarchy in terms of the `F[_, _]` type-constructor. Note again the variance annotation on `F`, it will be crucial for the ability to do type inference when composing algebras. Additionally, that's what allows us to define `F[Any, _]` as a value that doesn't have any dependencies, contravariance allows it to interoperate with any other `F[R, _]`.
 
 We can redefine our `Console` to use this newly introduced bi-functor:
 ```scala
trait Console[F[_, _]] {
  def console: Console.Service[F]
}

object Console {
  trait Service[F[_, _]] {
    def putStrLn(line: String): F[Any, Unit]

    val getStrLn: F[Any, String]
  }

  def apply[F[-_, _]](implicit reader: MonadReader[F]): Syntax[F] = new Syntax

  class Syntax[F[-_, _]](implicit reader: MonadReader[F]) {

    def putStrLn(line: String): F[Console[F], Unit] =
      reader.accessM(_.console.putStrLn(line))

    val getStrLn: F[Console[F], String] =
      reader.accessM(_.console.getStrLn)
  }
}
```

So we can now write:
```scala
class Programs[F[-_, _]:  MonadReader] {
  // Given the appropriate imports
  
  val complexInteraction =
    for {
      _ <- putStrLn("Good morning, what is your name?")
      name <- getStrLn
      _ <- savePreferences(name)
      _ <- debug("Saved $name to configuration")
      _ <- putStrLn(s"Good to meet you, $name!")
    } yield ()
}
```

This infers just as before, and given that `F` is set to be some ZIO-like type, we've also regained the performance loss we had from `ReaderT`. At the same time, there's no loss of testability, since our `ReaderT` type implements `MonadReader` as well, and so we can run our tests in something like `ReaderT[Writer, ?, ?]`. 

Taking a closer look at what we have, there's something a bit off about our code. Why does `Console` take a bi-functor `F[_, _]`? Seeing how the actual methods we define for `Console` return `F[Any, _]`, it's obvious that it doesn't really care about the first type-parameter. Which makes sense, `Console` doesn't depend on anything, so it doesn't need anything from the environment.

It looks like our `MonadReader` optimization polluted places that shouldn't really care about it. We can do better than that. 

## Regaining Mono-functoricity

Using the bi-functor context everywhere is suboptimal not only for the aesthetic reason mentioned above, but also due to the fact that one can imagine a mixed codebase, where both the reader and the standard finally tagless approaches are used in tandem. Polluting the basic algebras with the reader-like bi-functor will force the whole codebase to align to the new approach (of course, if you already are into ZIO, adding numerous type-parameters to everything shouldn't scare you all that much).

To fix the situation, we can note that the only part of `Console` that really requires a bi-functor is the syntax extension that we use to wrap everything in `MonadReader`. And so, using the [kind-projector plugin](https://github.com/non/kind-projector), we can juggle our types back in place:
```scala
trait Console[F[_]] {
  def console: Console.Service[F]
}

object Console {
  trait Service[F[_]] {
    def putStrLn(line: String): F[Unit]

    val getStrLn: F[String]
  }

  def apply[F[-_, _]](implicit reader: MonadReader[F]): Syntax[F] = new Syntax

  class Syntax[F[-_, _]](implicit reader: MonadReader[F]) {

    def putStrLn(line: String): F[Console[F[Any, ?]], Unit] =
      reader.accessM(_.console.putStrLn(line))

    val getStrLn: F[Console[F[Any, ?]], String] =
      reader.accessM(_.console.getStrLn)
  }
}
```

`Console` uses a single-argument type-constructor, while the `Syntax` class juggles the arity of the type-constructors to align everything with `MonadReader`. This works great till we take a look at the inferred type of the `complexInteraction` value:
```scala
F[Console[({ type Λ$[β] = F[Any, β] })#Λ$] with
  Logging[({ type Λ$[β] = F[Any, β] })#Λ$] with
  Persistence[({ type Λ$[β] = F[Any, β] })#Λ$], Unit]
```
If we are on the quest to gain ergonomics, that is definitely not it.

We can actually improve on the situation by doing some type-level trickery. Given the following definition:
```scala
trait With[F[_, _]] {
  type Any[A] = F[scala.Any, A]
}
```
This is essentially currying at the type-level.

We can now use this instead of kind-projector, to redefine `F[Any, ?]` as `With[F]#Any`. If we substitute this into our `Syntax` class from before, `complexInteraction` infers to:
```scala
F[Console[With[F]#Any] with Logging[With[F]#Any] with Persistence[With[F]#Any], Unit]
```

Which is not that terrible. Although, given the fact that one of the points raised against finally tagless is that it has a steep learning curve; adding type-level currying might not be the best way to make this topic more approachable.

The point of this whole exercise, was to show that given we are willing to accept some trade-offs, it is possible to achieve some of the benefits of using ZIO environment without leaving the finally tagless encoding behind. It's not ZIO or nothing, the gap between the new ZIO approach and the capabilities of finally tagless is not that big.

Now, whether or not you should apply any of these techniques to actual code is a wholly different matter. Like with any trade-off, this requires careful consideration as well as some subjective opinions. And this is the point where I want to step back and take a look at the situation that we have here.

## OOP Strikes Back

If you're still following along at home, you might have noticed a recurring theme throughout the post. We keep dealing with dependency-injection. Using implicits is one way to do it, using a reader-like abstraction is another. For the purposes of this post, the main difference between the two approaches is ergonomics within the limitations of the Scala language. 

How come this is an issue in the first place? The answer lies in the fact that we took the idea of programming to an interface to its full extent. If we take a look at the nature of these interfaces (stripping away the abstraction), we have:
```scala
trait Console {
  def putStrLn(line: String): IO[Unit]

  val getStrLn: IO[String]
}
```
If I had to ascribe a purely-functional type to a classic (stateful) OOP-style interface, this will be it. The OOP community has been struggling with issues around dependency injection for quite a few years. Now we are in the same boat, trying to inject around instances of "algebras", the main difference is in the tools we use.

Having diagnosed the actual issue we are trying to tackle, we can now restate the problem. It's not whether we need to choose between ZIO environment and finally tagless, but rather, which technique we want to use for dependency injection. Although the ergonomics of the chosen technique may dictate which of the two is more convenient, in general, the choice of effect-type/abstraction is orthogonal to the choice of a dependency injection technique. The code in this post is meant to serve as a proof of concept that we can lift the DI technique from ZIO to the rest of the world. And now we can move on to some opinions.

## But Seriously, What Should I Use?

I don't know what *you* should use, but I can try to state and motivate my personal preferences.

With respect to dependency injection, I prefer the simplest possible approach, just passing arguments around. Namely as constructor arguments, or if you prefer, module arguments. Yes, it's a bit tedious, and manually wiring the whole thing can be annoying, but I find that avoiding magic in this area makes life simpler in the long run (if you're so inclined, you can use [MacWire](https://github.com/adamw/macwire) to avoid some of the boilerplate). 

Of course, for maintainability's sake, one needs some discipline to make sure that the modules we define are small, that they don't expose too many functions, and that most functions that they do expose actually use the capabilities we endow them with. In return, when programming within a module, type-inference becomes a non-issue:
```scala
class Progarms[F[_]: Monad](console: Console[F], 
                            logging: Logging[F],
                            persistence: Persistence[F]) {
 import console._
 import logging._
 import persistence._
 
 val complexInteraction =
   for {
     _ <- putStrLn("Good morning, what is your name?")
     name <- getStrLn
     _ <- savePreferences(name)
     _ <- debug("Saved $name to configuration")
     _ <- putStrLn(s"Good to meet you, $name!")
   } yield ()
}
```
This trivially infers to `F[Unit]`. 

The problem of repetitive implicits is also minimized, since most of the time we only need one constraint from the standard `Monad` (or possibly an extended effect/concurrency) hierarchy, which I find reasonable (in the sense of easier to reason about). We no longer abuse typeclasses, as now our algebras are just simple records of functions, to be passed around as first class values. 

I've yet to address the question of whether actually abstracting over the context is worth the cognitive effort it requires. I think that testability and paramteric reasoning do make it worth the effort, but I'll defer the discussion about it to another (hopefully shorter) post.

## Conclusion

ZIO environment brings a new set of tradeoffs into the world of testable, pure functional programming. I hope that in this post I've managed to demonstrate that some of the techniques used by ZIO are applicable to the finally tagless encoding as well.

Although I don't think that ZIO environment is the death of finally tagless, as I see it, at the very least, John's talk and post managed to force a reexamining of the status quo.

Where one sees death, I see the rise of new, possibly more ergonomic, way of doing dependency injection in Scala. 

And since ergonomics are so high up the priority list for the ZIO developers, I think that quite possibly, ZIO can serve as a good gateway drug into the world of pure functional programming. Whether or not the drugs will escalate till final tagless is up for debate.

### Discussion

- [r/scala thread](https://www.reddit.com/r/scala/comments/ayhz0s/finally_tagless_not_quite_dead_yet/)
- [r/hascalator thread](https://www.reddit.com/r/hascalator/comments/ayhz79/finally_tagless_not_quite_dead_yet/)
