import microsites.MicrositesPlugin.autoImport._
import sbt._
import sbt.Keys._

object Microsite {

  // Micro site configurations.
  lazy val setting = Seq(
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

}
