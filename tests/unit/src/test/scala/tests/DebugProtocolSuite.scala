package tests
import java.util.Collections
import java.util.Collections.{emptyList, singletonList}
import java.util.concurrent.{Executors, TimeUnit}

import ch.epfl.scala.bsp4j.LaunchParametersDataKind.SCALA_MAIN_CLASS
import ch.epfl.scala.bsp4j.{
  BuildTargetIdentifier,
  DebugSessionParams,
  LaunchParameters,
  LaunchParametersDataKind,
  ScalaMainClass
}
import tests.CompletionSlowSuite.server

import scala.concurrent.duration.FiniteDuration
import scala.meta.internal.metals.ServerCommands

object DebugProtocolSuite extends BaseSlowSuite("debug-protocol") {
  override val sh = Executors.newScheduledThreadPool(4)

//  testAsync("launch", FiniteDuration(90, TimeUnit.SECONDS)) {
//    for {
//      _ <- server.initialize(
//        """/metals.json
//          |{
//          |  "a": {}
//          |}
//          |/a/src/main/scala/a/Main.scala
//          |package a
//          |object Main {
//          |  def main(args: Array[String]) = {
//          |    print("Hello, World!")
//          |  }
//          |}
//          |""".stripMargin
//      )
//      debugger <- server.startDebugging(
//        "a",
//        SCALA_MAIN_CLASS,
//        new ScalaMainClass("a.Main", emptyList(), emptyList())
//      )
//      _ <- debugger.initialize
//      _ <- debugger.launch
//      _ <- debugger.awaitCompletion
//    } yield assertNoDiff(debugger.output, "Hello, World!")
//  }

  testAsync("restart") {
    for {
      _ <- server.initialize(
        """/metals.json
          |{
          |  "a": {}
          |}
          |/a/src/main/scala/a/Main.scala
          |package a
          |object Main {
          |  def main(args: Array[String]) = {
          |    print("Hello, World!")
          |    Thread.sleep(1000)
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
      _ <- debugger.restart
      _ <- debugger.awaitCompletion
    } yield assertNoDiff(debugger.output, "Hello, World!\nHello, World!")
  }
}
