// Settings

import Build._
import Dependencies._

lazy val site = project
  .underModules
  .enablePlugins(MicrositesPlugin)
  .settings(Microsite.setting)

lazy val `example` = project
  .underModules
  .settings(shared)
  .settings(jvmForkSettings)
  .dependsOn(`machines`)

lazy val `benchmark` = project
  .underModules
  .settings(shared)
  .enablePlugins(JmhPlugin)
  .dependsOn(`machines`)

lazy val `machines` = project
  .underModules
  .settings(shared)
  .settings(libraryDependencies ++= librariesN)
  .settings(addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.8"))
  .settings(customTestFilter)
  .settings(jvmForkSettings)

lazy val `machines-root` = project
  .in(file("."))
  .aggregate(`machines`, `benchmark`, `example`, `site`)
