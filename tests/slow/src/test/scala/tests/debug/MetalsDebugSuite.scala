package tests.debug
import java.nio.file.Files
import java.nio.file.Paths

object MetalsDebugSuite extends MetalsBaseDebugSuite {
  testDebug("launch")(
    act = server => {
      server.launch(Sources.PrintHelloWorld)
    },
    validate = client => {
      assertEquals(client.output.stderr, "")
      assertEquals(client.output.stdout, "Hello, World!")
    }
  )

  testDebug("workingDir")(
    act = server => {
      server.launch(Sources.CreateFileInWorkingDir)
    },
    validate = client => {
      val path = Paths.get(DebugDirectory.workspace.resolve("out"))
      assert(Files.exists(path))
      Files.delete(path) // prevent false positives on consecutive runs
    }
  )
}
