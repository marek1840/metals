package tests
import java.util.Collections.emptyList
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit.SECONDS

import ch.epfl.scala.bsp4j.{RunParamsDataKind, ScalaMainClass}

import scala.concurrent.duration.FiniteDuration
import scala.meta.internal.metals.MetalsEnrichments._

object DebugProtocolSuite extends BaseSlowSuite("debug-protocol") {
  private val mainFile = "a/src/main/scala/a/Main.scala"

  testAsync("launch", FiniteDuration(90, TimeUnit.SECONDS)) {
    for {
      _ <- server.initialize(
        s"""/metals.json
           |{ "a": {} }
           |/$mainFile
           |package a
           |object Main {
           |  def main(args: Array[String]) = {
           |    println("Foo")
           |  }
           |}
           |""".stripMargin
      )
      _ <- server.didSave(mainFile)(x => x)
      debugger <- server.startDebugging(
        "a",
        RunParamsDataKind.SCALA_MAIN_CLASS,
        new ScalaMainClass("a.Main", emptyList(), emptyList())
      )
      _ <- debugger.initialize
      _ <- debugger.launch
      _ <- debugger.awaitCompletion
    } yield {
      scribe.info(debugger.output)
      assertNoDiff(debugger.output, "Foo\n")
    }
  }

  testAsync("restart") {
    for {
      _ <- server.initialize(
        s"""/metals.json
           |{ "a": {} }
           |/$mainFile
           |package a
           |object Main {
           |  def main(args: Array[String]) = {
           |    println("Foo")
           |    synchronized(wait())
           |  }
           |}
           |""".stripMargin
      )
      _ <- server.didSave(mainFile)(x => x)
      debugger <- server.startDebugging(
        "a",
        RunParamsDataKind.SCALA_MAIN_CLASS,
        new ScalaMainClass("a.Main", emptyList(), emptyList())
      )
      _ <- debugger.initialize
      _ <- debugger.launch
      _ <- debugger.awaitOutput("Foo\n").withTimeout(5, SECONDS)

      _ <- server.didSave(mainFile)(_.replaceAll("Foo", "Bar"))
      _ <- debugger.restart

      _ <- debugger.initialize
      _ <- debugger.launch
      _ <- debugger.awaitOutput("Bar\n").withTimeout(5, SECONDS)
      _ <- debugger.disconnect
      _ <- debugger.awaitCompletion
    } yield assertNoDiff(debugger.output, "Bar\n")
  }
}
