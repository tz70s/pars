import java.io

import sbt.{Def, _}
import sbt.Keys._
import spray.revolver.RevolverPlugin.autoImport.reStart

object Build {

  implicit class UnderModuleExtension(val project: Project) extends AnyVal {
    def underModules: Project = {
      val base = project.base.getParentFile / "modules" / project.base.getName
      project.in(base)
    }
  }

  lazy val shared: Seq[Def.Setting[_ >: String with Task[
    Seq[String]
  ] <: io.Serializable]] = Seq(
    version := "0.1",
    scalaVersion := "2.12.8",
    scalacOptions ++= Seq(
      "-deprecation",
      "-encoding",
      "utf-8",
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
  )

  val JvmOpts: Seq[String] = Seq(
    "-Xms512M",
    "-Xmx4G",
    "-XX:+UseG1GC",
    "-XX:MaxMetaspaceSize=256M",
    "-XX:MetaspaceSize=256M",
    "-Dcom.sun.management.jmxremote.authenticate=false",
    "-Dcom.sun.management.jmxremote.ssl=false",
    "-Djava.rmi.server.hostname=localhost"
  )

  lazy val jvmForkSettings: Seq[Def.Setting[_ >: Boolean with Task[
    Seq[String]
  ] with Option[OutputStrategy]]] = Seq(
    run / fork := true,
    run / javaOptions ++= JvmOpts,
    Test / fork := true,
    Test / javaOptions ++= JvmOpts,
    reStart / javaOptions ++= JvmOpts,
    outputStrategy := Some(StdoutOutput)
  )

  lazy val customTestFilter: Seq[Def.Setting[Task[Seq[TestOption]]]] = Seq(
    Test / testOptions := Seq(Tests.Filter(s => !(s.contains("Socket") || s.contains("Curator"))))
  )
}
