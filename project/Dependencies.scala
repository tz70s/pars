import sbt._
import sbt.Keys._

object Dependencies {

  ThisBuild / resolvers += Resolver.sonatypeRepo("releases")

  // FS2-based impl dependencies.
  val FS2 = "co.fs2"
  val FS2Version = "1.0.2"
  val FS2Core = FS2 %% "fs2-core" % FS2Version
  val FS2IO = FS2 %% "fs2-io" % FS2Version

  // Alternatives
  val scalaTestVersion = "3.0.5"
  val scalaTest = "org.scalatest" %% "scalatest" % scalaTestVersion % Test

  val logback = "ch.qos.logback" % "logback-classic" % "1.2.3"
  val log4cats = "io.chrisdavenport" %% "log4cats-slf4j" % "0.2.0"

  val pureConfig = "com.github.pureconfig" %% "pureconfig" % "0.10.1"

  val curatorVersion = "4.1.0"
  val curator = "org.apache.curator" % "curator-recipes" % curatorVersion
  val curatorAsync = "org.apache.curator" % "curator-x-async" % curatorVersion
  val curatorTest = "org.apache.curator" % "curator-test" % curatorVersion % Test

  val curators: Seq[ModuleID] = Seq(curator, curatorAsync, curatorTest)

  val librariesN: Seq[ModuleID] = Seq(FS2Core, FS2IO, scalaTest, pureConfig, log4cats, logback) ++ curators
}
