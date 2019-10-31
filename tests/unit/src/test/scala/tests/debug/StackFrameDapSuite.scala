package tests.debug

import java.util.Collections.emptyList
import ch.epfl.scala.bsp4j.DebugSessionParamsDataKind
import ch.epfl.scala.bsp4j.ScalaMainClass
import tests.BaseLspSuite
import scala.meta.inputs.Input
import scala.meta.inputs.Position
import scala.meta.internal.metals.debug.BreakpointHit
import scala.meta.internal.metals.debug.Scope
import scala.meta.internal.metals.debug.Variable

object StackFrameDapSuite extends BaseLspSuite("stack-frame") {
  assertStackFrame("foreach")(
    source = """|object Main {
                |  def main(args: Array[String]) = {
                |    List(1, 2).foreach { value =>
                |>>      println(value)
                |    }
                |  }
                |}""".stripMargin,
    expectedHits = List(
      BreakpointHit(
        Scope.local(Variable("value: int = 1"))
      ),
      BreakpointHit(
        Scope.local(Variable("value: int = 2"))
      )
    )
  )

  assertStackFrame("method-parameters")(
    source = """|object Main {
                |  def main(args: Array[String]): Unit = {
                |>>  println()
                |  }
                |}
                |""".stripMargin,
    expectedHits = List(
      BreakpointHit(
        Scope.local(Variable("this: Main$"), Variable("args: String[]"))
      )
    )
  )

  assertStackFrame("primitives")(
    source = """|object Main {
                |  def main(args: Array[String]): Unit = {
                |    foo()
                |  }
                |  
                |  def foo(): Unit = {
                |    val aByte = 1.toByte
                |    val aShort = 1.toShort
                |    val anInt  = 1
                |    val aLong  = 1L
                |    val aFloat = 1.0f
                |    val aDouble = 1.0
                |    val bool = true
                |    val aChar = 'a'
                |>>  println()
                |  }
                |}
                |""".stripMargin,
    expectedHits = List(
      BreakpointHit(
        Scope.local(
          Variable("aByte: byte = 1"),
          Variable("aShort: short = 1"),
          Variable("anInt: int = 1"),
          Variable("aLong: long = 1"),
          Variable("aFloat: float = 1.000000"),
          Variable("aDouble: double = 1.000000"),
          Variable("bool: boolean = true"),
          Variable("aChar: char = a"),
          Variable("this: Main$")
        )
      )
    )
  )

  def assertStackFrame(
      name: String
  )(source: String, expectedHits: List[BreakpointHit]): Unit = {
    testAsync(name) {
      cleanWorkspace()
      val (text, position): (String, Position.Range) = {
        val offset = source.indexOf(">>")
        val text = source.replaceAllLiterally(">>", "")
        val pos = Position.Range(Input.String(text), offset, offset)
        (text, pos)
      }

      for {
        _ <- server.initialize(
          s"""/metals.json
             |{
             |  "a": {}
             |}
             |/a/src/main/scala/a/Main.scala
             |$text
             |""".stripMargin
        )
        file = server.toPath("a/src/main/scala/a/Main.scala")
        debugger <- server.startDebugging(
          "a",
          DebugSessionParamsDataKind.SCALA_MAIN_CLASS,
          new ScalaMainClass("Main", emptyList(), emptyList())
        )
        _ <- debugger.initialize
        _ <- debugger.launch
        _ <- debugger.setBreakpoints(file, position)
        _ <- debugger.configurationDone
        _ <- debugger.awaitCompletion
        breakpoints = debugger.breakpointUsage
      } yield {
        assertEquals(breakpoints.values.flatten.mkString, expectedHits.mkString)
      }
    }
  }
}
