name := "finally-tagless-not-quite-dead"

version := "0.1"

scalaVersion := "2.12.8"

resolvers += Resolver.sonatypeRepo("releases")

addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.9")

libraryDependencies ++= Seq(
  "org.scalaz" %% "scalaz-core" % "7.2.27",
)