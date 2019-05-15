package tests.debug

import java.net.URI
import java.nio.file.Files

protected[debug] object DebugDirectory {
  val moduleName: String = "foo"
  val workspace: URI = createWorkspace()

  val metalsJson: URI = workspace.resolve("metals.json")

  val sourceDir: URI = workspace.resolve(s"$moduleName/src/main/scala/")
  def sourceFile(name: String): URI = sourceDir.resolve(s"$name.scala")

  private def createWorkspace(): URI = {
    val path = Files.createTempDirectory("metals-debug")
    path.toFile.deleteOnExit()
    path.toUri
  }
}
