package tests.debug
import org.eclipse.lsp4j.debug.Breakpoint
import tests.BaseLspSuite
import scala.concurrent.Future
import scala.meta.internal.metals.debug.BreakpointHit
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

    sealed trait DebuggerState
    final case object Running extends DebuggerState
    final case class Paused(breakpoint: Breakpoint, hit: BreakpointHit)

    final class WhenAt(location: String) {
      def stepInTo(expectedLocation: String): WhenAt = ???
      def stepOutTo(expectedLocation: String): WhenAt = ???
      def stepOverTo(expectedLocation: String): WhenAt = ???
      def continueTo(expectedLocation: String): WhenAt = ???
      def continue(): WhenAt = ???
    }

    new WhenAt("a.scala:5")
      .stepInTo("b.scala:7")
      .stepOutTo("a.scala:9")
      .continueTo("foo.java:35")
      .continue()

    ???
  }
}
