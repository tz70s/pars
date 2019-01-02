// Self-contained, lightweight distributed computation library for data-intensive workload.
// Zero distributed components required like Zookeeper, Kafka, etc.

ThisBuild / name := "task4s"
ThisBuild / version := "0.1"
ThisBuild / scalaVersion := "2.12.8"
ThisBuild / scalacOptions += "-Ypartial-unification"

// Akka dependencies.
val akkaVersion = "2.5.19"
val akkaId = "com.typesafe.akka"
val akkaActorTyped = akkaId %% "akka-actor-typed" % akkaVersion
val akkaActorTypedTeskit = akkaId %% "akka-actor-testkit-typed" % akkaVersion
val akkaClusterTyped = akkaId %% "akka-cluster-typed" % akkaVersion
val akkaClusterShardingTyped = akkaId %% "akka-cluster-sharding-typed" % akkaVersion
val akkaStreamTyped = akkaId %% "akka-stream-typed" % akkaVersion

lazy val akkas = Seq(akkaActorTyped, akkaActorTypedTeskit, akkaClusterTyped, akkaClusterShardingTyped, akkaStreamTyped)

// Alternatives
val scalaTestVersion = "3.0.5"
val scalaTest = "org.scalatest" %% "scalatest" % scalaTestVersion % Test

val logback = "ch.qos.logback" % "logback-classic" % "1.2.3"
val logging = "com.typesafe.scala-logging" %% "scala-logging" % "3.9.0"

val FS2 = "co.fs2"
val FS2Version = "1.0.2"
val FS2Core = FS2 %% "fs2-core" % FS2Version
val FS2IO = FS2 %% "fs2-io" % FS2Version

lazy val FS2s = Seq(FS2Core, FS2IO)

lazy val libraries = Seq(scalaTest) ++ akkas

// Micro site configurations.
lazy val micrositeConf = Seq(
  micrositeName := "task4s",
  micrositeDescription := "Tasks for Data-Intensive Applications",
  micrositeAuthor := "Tzu-Chiao Yeh",
  micrositeHighlightTheme := "atom-one-light",
  micrositeGitterChannel := false,
  micrositeGithubOwner := "tz70s",
  micrositeGithubRepo := "task4s",
  micrositeBaseUrl := "/task4s",
  libraryDependencies += "com.47deg" %% "github4s" % "0.19.0",
  micrositePushSiteWith := GitHub4s,
  micrositeGithubToken := sys.env.get("GITHUB_TOKEN"),
  micrositePalette := Map(
    "brand-primary" -> "#336666",
    "brand-secondary" -> "#408080",
    "brand-tertiary" -> "#408080",
    "gray-dark" -> "#192946",
    "gray" -> "#424F67",
    "gray-light" -> "#E3E2E3",
    "gray-lighter" -> "#F4F3F4",
    "white-color" -> "#FFFFFF"
  )
)
lazy val task4s = (project in file("task4s"))
  .settings(libraryDependencies ++= libraries)

lazy val site = (project in file("site"))
  .enablePlugins(MicrositesPlugin)
  .settings(micrositeConf)

lazy val example = (project in file("example"))
  .dependsOn(task4s)

lazy val `task4s-jmh` = (project in file("task4s-jmh"))
  .enablePlugins(JmhPlugin)
  .dependsOn(task4s)

lazy val `task4s-fs2` = (project in file("task4s-fs2"))
  .settings(libraryDependencies ++= FS2s ++ Seq(logback, logging))

lazy val root = (project in file("."))
  .aggregate(task4s, site, example, `task4s-jmh`)
