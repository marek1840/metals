package tests.debug
import org.eclipse.lsp4j.debug.Breakpoint
import tests.BaseLspSuite
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.meta.internal.metals.debug.Variables
import scala.meta.internal.metals.debug.DebugSteps
import scala.meta.internal.metals.debug.TestDebugger
import scala.meta.internal.metals.debug.VMPause

object BreakpointStepDapSuite extends BaseLspSuite("breakpoint-step") {
  testAsync("step-into-java") {
    val scalaSource =
      """|object ScalaMain {
         |  def main(args: Array[String]): Unit = {
         |>>  JavaClass.foo(7)
         |  }
         |}
         |""".stripMargin
    val javaSource =
      """|class JavaClass {
         |  static void foo(int i){
         |   System.out.println(i);
         |  }
         |}
         |""".stripMargin

    whenAt("a.scala", 5)
      .stepInTo("b.scala:7")
      .stepOutTo("a.scala:9")
      .continueTo("foo.java:35")
      .continue()

    ???
  }

  testAsync("step-in-java") {
    val foo: DebugSteps = ???

    DebugSteps.whenAt("b.java", 4)
    foo.stepInTo("b.java", 4)
  }
}
