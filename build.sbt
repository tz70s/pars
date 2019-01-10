// Self-contained, lightweight distributed computation library for data-intensive workload.
// Zero distributed components required like Zookeeper, Kafka, etc.

ThisBuild / name := "task4s"
ThisBuild / version := "0.1"
ThisBuild / scalaVersion := "2.12.8"
ThisBuild / scalacOptions ++= Seq(
  "-deprecation",
  "-encoding", "utf-8",
  "-explaintypes",
  "-feature",
  "-language:existentials",
  "-language:experimental.macros",
  "-language:implicitConversions",
  "-unchecked",
  "-Ypartial-unification",
  "-language:higherKinds",
  "-Ywarn-infer-any"
)

// Akka dependencies.
val akkaVersion = "2.5.19"
val akkaId = "com.typesafe.akka"
val akkaActorTyped = akkaId %% "akka-actor-typed" % akkaVersion
val akkaActorTypedTeskit = akkaId %% "akka-actor-testkit-typed" % akkaVersion
val akkaClusterTyped = akkaId %% "akka-cluster-typed" % akkaVersion
val akkaClusterShardingTyped = akkaId %% "akka-cluster-sharding-typed" % akkaVersion
val akkaStreamTyped = akkaId %% "akka-stream-typed" % akkaVersion

lazy val akkas = Seq(akkaActorTyped, akkaActorTypedTeskit, akkaClusterTyped, akkaClusterShardingTyped, akkaStreamTyped)

// FS2-based impl dependencies.
val FS2 = "co.fs2"
val FS2Version = "1.0.2"
val FS2Core = FS2 %% "fs2-core" % FS2Version
val FS2IO = FS2 %% "fs2-io" % FS2Version

// Alternatives
val scalaTestVersion = "3.0.5"
val scalaTest = "org.scalatest" %% "scalatest" % scalaTestVersion % Test
val scalactic = "org.scalatest" %% "scalactic" % scalaTestVersion

val logback = "ch.qos.logback" % "logback-classic" % "1.2.3"
val log4cats = "io.chrisdavenport" %% "log4cats-slf4j" % "0.2.0"

val pureConfig = "com.github.pureconfig" %% "pureconfig" % "0.10.1"

val librariesN = Seq(FS2Core, FS2IO, scalaTest, pureConfig, log4cats, logback)

val catsEffect = "org.typelevel" %% "cats-effect" % "1.1.0"

lazy val libraries = Seq(scalaTest, catsEffect) ++ akkas

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

val JvmOpts = Seq(
  "-Xms512M",
  "-Xmx4G",
  "-XX:+UseG1GC",
  "-Dcom.sun.management.jmxremote.authenticate=false",
  "-Dcom.sun.management.jmxremote.ssl=false",
  "-Djava.rmi.server.hostname=localhost"
)

lazy val jvmForkSettings = Seq(
  run / fork := true,
  run / javaOptions ++= JvmOpts,
  reStart / javaOptions ++= JvmOpts,
  outputStrategy := Some(StdoutOutput)
)

lazy val task4s = (project in file("task4s"))
  .settings(libraryDependencies ++= libraries)

lazy val site = (project in file("site"))
  .enablePlugins(MicrositesPlugin)
  .settings(micrositeConf)

lazy val example = (project in file("example"))
  .dependsOn(task4s)
  .settings(jvmForkSettings)

lazy val `task4s-jmh` = (project in file("task4s-jmh"))
  .enablePlugins(JmhPlugin)
  .dependsOn(task4s, example)

lazy val `task4s-fs2` = (project in file("task4s-fs2"))
  .settings(libraryDependencies ++= librariesN)

lazy val root = (project in file("."))
  .aggregate(task4s, site, example, `task4s-jmh`)
