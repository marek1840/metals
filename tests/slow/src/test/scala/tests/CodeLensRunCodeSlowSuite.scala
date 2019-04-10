package tests

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
           |/a/src/main/scala/a/Main.scala
           |object Main {
           |  def main(args: Array[String]): Unit = {
           |    println("Hello, World!")
           |  }
           |}
           |""".stripMargin
      )
      _ <- server.didOpen("a/src/main/scala/a/Main.scala")
      result <- server.executeCodeLens("a/src/main/scala/a/Main.scala", RunCode)
    } yield assertEquals(result.getStatusCode, StatusCode.OK)
  }
}
