package tests.debug
import java.net.URI
import java.nio.file.Files

protected[debug] object DebugDirectory {
  val moduleName: String = "foo"
  val workspace: URI = Files.createTempDirectory("metals-debug").toUri

  val metalsJson: URI = workspace.resolve("metals.json")

  val sourceDir: URI = workspace.resolve(s"$moduleName/src/main/scala/")
  def sourceFile(name: String): URI = sourceDir.resolve(s"$name.scala")
}
