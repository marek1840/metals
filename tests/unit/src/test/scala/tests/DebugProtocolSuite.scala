package tests
import java.util.Collections.emptyList
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.{Executors, TimeUnit}

import ch.epfl.scala.bsp4j.LaunchParametersDataKind.SCALA_MAIN_CLASS
import ch.epfl.scala.bsp4j.ScalaMainClass

import scala.concurrent.duration.FiniteDuration
import scala.meta.internal.metals.MetalsEnrichments._

object DebugProtocolSuite extends BaseSlowSuite("debug-protocol") {
  private val mainFile = "a/src/main/scala/a/Main.scala"

  testAsync("launch", FiniteDuration(90, TimeUnit.SECONDS)) {
    for {
      _ <- server.initialize(
        s"""/metals.json
           |{
           |  "a": {}
           |}
           |/$mainFile
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
        SCALA_MAIN_CLASS,
        new ScalaMainClass("a.Main", emptyList(), emptyList())
      )
      _ <- debugger.initialize
      _ <- debugger.launch
      _ <- debugger.awaitCompletion
    } yield assertNoDiff(debugger.output, "Foo")
  }

  testAsync("restart") {
    for {
      _ <- server.initialize(
        s"""/metals.json
           |{
           |  "a": {}
           |}
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
      debugger <- server.startDebugging(
        "a",
        SCALA_MAIN_CLASS,
        new ScalaMainClass("a.Main", emptyList(), emptyList())
      )
      _ <- debugger.initialize
      _ <- debugger.launch
      _ <- debugger.awaitOutput("Foo\n").withTimeout(5, SECONDS)
      _ <- debugger.restart
      _ <- debugger.awaitOutput("Foo\nFoo").withTimeout(5, SECONDS)
      _ <- debugger.disconnect
      _ <- debugger.awaitCompletion
    } yield assertNoDiff(debugger.output, "Foo\nFoo\n")
  }
}
