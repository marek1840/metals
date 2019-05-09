package tests

import java.util.Collections
import ch.epfl.scala.bsp4j.CompileParams
import ch.epfl.scala.bsp4j.StatusCode
import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.Duration
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
           |object Main {
           |  def main(args: Array[String]): Unit = {
           |    println("Hello, World!")
           |  }
           |}
           |""".stripMargin
      )
      result <- server.executeCodeLens(
        "a/src/main/scala/Main.scala",
        RunCode
      )
    } yield assertEquals(result.getStatusCode, StatusCode.OK)
  }
}
