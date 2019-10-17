package tests.debug

import java.util.Collections.emptyList
import ch.epfl.scala.bsp4j.DebugSessionParamsDataKind
import ch.epfl.scala.bsp4j.ScalaMainClass
import tests.BaseLspSuite
import scala.meta.inputs.Input
import scala.meta.inputs.Position
import scala.meta.internal.metals.debug.DebugStep._
import scala.meta.internal.metals.debug.StepNavigator

object DebugStepDapSuite extends BaseLspSuite("breakpoint-step") {
  testAsync("step-into-java") {
    cleanWorkspace()

    val source = """|package a
                    |
                    |object ScalaMain {
                    |  def main(args: Array[String]): Unit = {
                    |>>  JavaClass.foo(7)
                    |  }
                    |}""".stripMargin

    val (mainClass, breakpointPosition) = extractBreakpointPosition(source)

    for {
      _ <- server.initialize(
        s"""/metals.json
           |{
           |  "a": {}
           |}
           |/a/src/main/scala/a/ScalaMain.scala
           |$mainClass
           |
           |/a/src/main/java/a/JavaClass.java
           |package a;
           |class JavaClass {
           |  static void foo(int i){
           |    System.out.println(i);
           |  }
           |}
           |""".stripMargin
      )
      scalaMain = server.toPath("a/src/main/scala/a/ScalaMain.scala")
      javaClass = server.toPath("a/src/main/java/a/JavaClass.java")
      stepNavigator = StepNavigator()
        .at(scalaMain, line = 4)(StepIn)
        .at(javaClass, line = 3)(StepOut)
        .at(scalaMain, line = 4)(Continue)

      debugger <- server.startDebugging(
        "a",
        DebugSessionParamsDataKind.SCALA_MAIN_CLASS,
        new ScalaMainClass("a.ScalaMain", emptyList(), emptyList()),
        stoppageHandler = stepNavigator
      )
      _ <- debugger.initialize
      _ <- debugger.launch
      _ <- debugger.setBreakpoints(scalaMain, breakpointPosition)
      _ <- debugger.configurationDone
      _ <- debugger.shutdown
      _ <- stepNavigator.assertAllStepsReached()
    } yield ()
  }

  testAsync("step-into-scala-lib") {
    cleanWorkspace()

    val source = """|package a
                    |
                    |object Main {
                    |  def main(args: Array[String]): Unit = {
                    |>>  println("foo")
                    |  }
                    |}
                    |""".stripMargin

    val (mainClass, breakpointPosition) = extractBreakpointPosition(source)

    for {
      _ <- server.initialize(
        s"""/metals.json
           |{
           |  "a": {}
           |}
           |
           |/a/src/main/scala/Main.scala
           |$mainClass
           |""".stripMargin
      )
      scalaMain = workspace.resolve("a/src/main/scala/Main.scala")
      predef = workspace.resolve(".metals/readonly/scala/Predef.scala")
      stepNavigator = StepNavigator()
        .at(scalaMain, line = 4)(StepIn)
        .at(predef, line = 396)(Continue)

      debugger <- server.startDebugging(
        "a",
        DebugSessionParamsDataKind.SCALA_MAIN_CLASS,
        new ScalaMainClass("a.Main", emptyList(), emptyList()),
        stoppageHandler = stepNavigator
      )
      _ <- debugger.initialize
      _ <- debugger.launch
      _ <- debugger.setBreakpoints(scalaMain, breakpointPosition)
      _ <- debugger.configurationDone
      _ <- debugger.shutdown
      _ <- stepNavigator.assertAllStepsReached()
    } yield ()
  }

  testAsync("step-into-java-lib") {
    cleanWorkspace()

    val source = """|package a
                    |
                    |object Main {
                    |  def main(args: Array[String]): Unit = {
                    |>>  System.out.println("foo")
                    |  }
                    |}
                    |""".stripMargin

    val (mainClass, breakpointPosition) = extractBreakpointPosition(source)

    for {
      _ <- server.initialize(
        s"""/metals.json
           |{
           |  "a": {}
           |}
           |
           |/a/src/main/scala/Main.scala
           |$mainClass
           |""".stripMargin
      )
      scalaMain = workspace.resolve("a/src/main/scala/Main.scala")
      predef = workspace.resolve(".metals/readonly/java/io/PrintStream.java")
      stepNavigator = StepNavigator()
        .at(scalaMain, line = 4)(StepIn)
        .at(predef, line = 804)(Continue)

      debugger <- server.startDebugging(
        "a",
        DebugSessionParamsDataKind.SCALA_MAIN_CLASS,
        new ScalaMainClass("a.Main", emptyList(), emptyList()),
        stoppageHandler = stepNavigator
      )
      _ <- debugger.initialize
      _ <- debugger.launch
      _ <- debugger.setBreakpoints(scalaMain, breakpointPosition)
      _ <- debugger.configurationDone
      _ <- debugger.shutdown
      _ <- stepNavigator.assertAllStepsReached()
    } yield ()
  }

  testAsync("same-file-different-class") {
    cleanWorkspace()

    val source = """|package a
                    |
                    |object Main {
                    |  def main(args: Array[String]): Unit = {
                    |    val foo = new Foo
                    |>>  System.out.println(foo.call)
                    |  }
                    |}
                    |
                    |class Foo {
                    |  def call = "foo"
                    |}
                    |""".stripMargin

    val (mainClass, breakpointPosition) = extractBreakpointPosition(source)

    for {
      _ <- server.initialize(
        s"""/metals.json
           |{
           |  "a": {}
           |}
           |
           |/a/src/main/scala/Main.scala
           |$mainClass
           |""".stripMargin
      )
      scalaMain = workspace.resolve("a/src/main/scala/Main.scala")
      stepNavigator = StepNavigator()
        .at(scalaMain, line = 5)(StepIn)
        .at(scalaMain, line = 10)(Continue)

      debugger <- server.startDebugging(
        "a",
        DebugSessionParamsDataKind.SCALA_MAIN_CLASS,
        new ScalaMainClass("a.Main", emptyList(), emptyList()),
        stoppageHandler = stepNavigator
      )
      _ <- debugger.initialize
      _ <- debugger.launch
      _ <- debugger.setBreakpoints(scalaMain, breakpointPosition)
      _ <- debugger.configurationDone
      _ <- debugger.shutdown
      _ <- stepNavigator.assertAllStepsReached()
    } yield ()
  }

  def extractBreakpointPosition(source: String): (String, Position.Range) = {
    val offset = source.indexOf(">>")
    val text = source.replaceAllLiterally(">>", "")
    val pos = Position.Range(Input.String(text), offset, offset)
    (text, pos)
  }
}
