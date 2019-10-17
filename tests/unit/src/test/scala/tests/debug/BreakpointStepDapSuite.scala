package tests.debug

import scala.meta.internal.metals.debug.DebugStep._
import java.util.Collections.emptyList
import ch.epfl.scala.bsp4j.DebugSessionParamsDataKind
import ch.epfl.scala.bsp4j.ScalaMainClass
import tests.BaseLspSuite
import tests.debug.StackFrameDapSuite.assertEquals
import tests.debug.StackFrameDapSuite.cleanWorkspace
import tests.debug.StackFrameDapSuite.server
import tests.debug.StackFrameDapSuite.testAsync
import scala.meta.inputs.Input
import scala.meta.inputs.Position
import scala.meta.internal.metals.debug.DebugStep
import scala.meta.internal.metals.debug.StepNavigator
import scala.meta.internal.metals.debug.VariableHoarder

object BreakpointStepDapSuite extends BaseLspSuite("breakpoint-step") {
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
        .at(scalaMain, 4)(StepIn)
        .at(javaClass, 3)(StepOut)
        .at(scalaMain, 4)(Continue)

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
      _ <- debugger.awaitCompletion
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
