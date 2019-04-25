package scala.meta.internal.metals

import ch.epfl.scala.{bsp4j => b}

/**
 * Commands issued by code lenses
 */
object CodeLensCommands {
  val RunCode = Command[b.RunResult](
    "code-run",
    "run",
    "runs code", // TODO,
    "`string`, source file path"
  )

  val RunTest = Command[b.TestResult](
    "run-test",
    "test",
    "runs test", // TODO,
    "`string`, source file path"
  )
}
