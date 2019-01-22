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
  .dependsOn(`pars`)

lazy val `benchmark` = project
  .underModules
  .settings(shared)
  .enablePlugins(JmhPlugin)
  .dependsOn(`pars`)

lazy val `pars` = project
  .underModules
  .settings(shared)
  .settings(libraryDependencies ++= librariesN)
  .settings(addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.8"))
  .settings(customTestFilter)
  .settings(jvmForkSettings)

lazy val `pars-root` = project
  .in(file("."))
  .aggregate(`pars`, `benchmark`, `example`, `site`)
