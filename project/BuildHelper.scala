import sbt._

object BuildHelper {
  def packageFiles(outputDir: File, distDir: File, manifestSrc: File, iconsSrc: File): Unit = {
    IO.delete(distDir)
    IO.createDirectory(distDir)
    IO.copyFile(outputDir / "main.js", distDir / "main.js")
    val mapFile = outputDir / "main.js.map"
    if (mapFile.exists) IO.copyFile(mapFile, distDir / "main.js.map")
    IO.copyFile(manifestSrc, distDir / "manifest.json")
    IO.copyDirectory(iconsSrc, distDir / "icons")
  }
}
