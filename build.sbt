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

val packageDev = taskKey[File]("Package the extension into dist/ (fast, for development)")
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
    packageDev := {
      val outputDir = (Compile / crossTarget).value / s"${(Compile / moduleName).value}-fastopt"
      val manifestSrc = (Compile / resourceDirectory).value / "manifest.json"
      val iconsSrc = (Compile / resourceDirectory).value / "icons"
      val _ = (Compile / fastLinkJS).value
      IO.createDirectory(distDir.value)
      IO.copyFile(outputDir / "main.js", distDir.value / "main.js")
      val mapFile = outputDir / "main.js.map"
      if (mapFile.exists) IO.copyFile(mapFile, distDir.value / "main.js.map")
      IO.copyFile(manifestSrc, distDir.value / "manifest.json")
      IO.copyDirectory(iconsSrc, distDir.value / "icons")
      streams.value.log.info(s"Extension packaged to ${distDir.value}")
      distDir.value
    },
  )
