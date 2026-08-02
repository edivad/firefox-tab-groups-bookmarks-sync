ThisBuild / scalaVersion := "3.3.7"
ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalacOptions ++= Seq(
  "-Xfatal-warnings",
  "-deprecation",
  "-feature",
  "-unchecked",
  "-Wunused:imports",
  "-Wnonunit-statement",
  "-explain",
  "-no-indent",
)

val packageFast = taskKey[File]("Package the extension into dist/ (fastLinkJS, for development)")
val packageRelease = taskKey[File]("Package the extension into dist/ (fullLinkJS, optimized, for release)")
val distDir = settingKey[File]("Extension output directory")

lazy val root = project
  .in(file("."))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    name := "firefox-tab-groups-bookmarks-sync",
    scalaJSUseMainModuleInitializer := true,
    distDir := (ThisBuild / baseDirectory).value / "dist",
    clean := {
      clean.value
      IO.delete(distDir.value)
    },
    libraryDependencies ++= Seq(
      "org.scalameta" %%% "munit" % "1.3.3" % Test,
      "org.scalameta" %%% "munit-scalacheck" % "1.3.0" % Test,
    ),
    packageFast := {
      val _ = (Compile / fastLinkJS).value
      val outputDir = (Compile / crossTarget).value / s"${(Compile / moduleName).value}-fastopt"
      BuildHelper.packageFiles(outputDir, distDir.value, (Compile / resourceDirectory).value / "manifest.json", (Compile / resourceDirectory).value / "icons")
      streams.value.log.info(s"Extension packaged to ${distDir.value}")
      distDir.value
    },
    packageRelease := {
      val _ = (Compile / fullLinkJS).value
      val outputDir = (Compile / crossTarget).value / s"${(Compile / moduleName).value}-opt"
      BuildHelper.packageFiles(outputDir, distDir.value, (Compile / resourceDirectory).value / "manifest.json", (Compile / resourceDirectory).value / "icons")
      streams.value.log.info(s"Extension packaged to ${distDir.value}")
      distDir.value
    },
  )
