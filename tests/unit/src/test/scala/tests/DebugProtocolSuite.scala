package tests

import java.util.Collections.emptyList
import java.util.concurrent.TimeUnit.SECONDS
import ch.epfl.scala.bsp4j.ScalaMainClass
import ch.epfl.scala.bsp4j.DebugSessionParamsDataKind
import scala.meta.internal.metals.MetalsEnrichments._
import scala.concurrent.duration.Duration
import scala.concurrent.Future

object DebugProtocolSuite extends BaseLspSuite("debug-protocol") {

  override def testAsync(
      name: String,
      maxDuration: Duration = Duration("3min")
  )(run: => Future[Unit]): Unit = {
    if (BaseSuite.isWindows) {
      // Currently not working on Windows
      ignore(name) {}
    } else {
      super.testAsync(name, maxDuration)(run)
    }
  }

  testAsync("start") {
    for {
      _ <- server.initialize(
        s"""/metals.json
           |{
           |  "a": {}
           |}
           |/a/src/main/scala/a/Main.scala
           |package a
           |object Main {
           |  def main(args: Array[String]) = {
           |    print("Foo")
           |  }
           |}
           |""".stripMargin
      )
      debugger <- server.startDebugging(
        "a",
        DebugSessionParamsDataKind.SCALA_MAIN_CLASS,
        new ScalaMainClass("a.Main", emptyList(), emptyList())
      )
      _ <- debugger.initialize
      _ <- debugger.start
      _ <- debugger.awaitCompletion
    } yield assertNoDiff(debugger.output, "Foo")
  }

  testAsync("disconnect") {
    for {
      _ <- server.initialize(
        s"""/metals.json
           |{
           |  "a": {}
           |}
           |/a/src/main/scala/a/Main.scala
           |package a
           |object Main {
           |  def main(args: Array[String]) = { 
           |    synchronized(wait())
           |  }
           |}
           |""".stripMargin
      )
      debugger <- server.startDebugging(
        "a",
        DebugSessionParamsDataKind.SCALA_MAIN_CLASS,
        new ScalaMainClass("a.Main", emptyList(), emptyList())
      )
      _ <- debugger.initialize
      _ <- debugger.start
      _ <- debugger.disconnect
      _ <- debugger.awaitCompletion
    } yield assertNoDiff(debugger.output, "")
  }

  testAsync("restart") {
    for {
      _ <- server.initialize(
        s"""/metals.json
           |{
           |  "a": {}
           |}
           |/a/src/main/scala/a/Main.scala
           |package a
           |object Main {
           |  def main(args: Array[String]) = {
           |    println("Foo")
           |    synchronized(wait())
           |  }
           |}
           |""".stripMargin
      )
      debugger <- server.startDebugging(
        "a",
        DebugSessionParamsDataKind.SCALA_MAIN_CLASS,
        new ScalaMainClass("a.Main", emptyList(), emptyList())
      )
      _ <- debugger.initialize
      _ <- debugger.start
      _ <- debugger.awaitOutput("Foo\n").withTimeout(5, SECONDS)

      _ <- server.didSave("a/src/main/scala/a/Main.scala")(
        _.replaceAll("Foo", "Bar")
      )
      _ <- debugger.restart

      _ <- debugger.initialize
      _ <- debugger.start
      _ <- debugger.awaitOutput("Bar\n").withTimeout(5, SECONDS)
      _ <- debugger.disconnect
      _ <- debugger.awaitCompletion
    } yield assertNoDiff(debugger.output, "Bar\n")
  }
}
