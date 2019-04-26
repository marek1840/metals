package tests

import java.util.Collections
import ch.epfl.scala.bsp4j.CompileParams
import ch.epfl.scala.bsp4j.StatusCode
import scala.meta.internal.metals.CodeLensCommands.RunCode

object CodeLensRunCodeSlowSuite extends BaseSlowSuite("codeLens/run") {
  testAsync("run-code") {
    for {
      _ <- server.initialize(
        """|
           |/metals.json
           |{
           |  "a": { }
           |}
           |
           |/a/src/main/scala/Main.scala
           |""".stripMargin
      )
      // force compilation - required by run code lenses
      _ <- server.didSave("a/src/main/scala/Main.scala") { _ =>
        """object Main {
          |  def main(args: Array[String]): Unit = {
          |    println("Hello, World!")
          |  }
          |}
          |""".stripMargin
      }
      result <- server.executeCodeLens(
        "a/src/main/scala/Main.scala",
        RunCode
      )
    } yield assertEquals(result.getStatusCode, StatusCode.OK)
  }
}
