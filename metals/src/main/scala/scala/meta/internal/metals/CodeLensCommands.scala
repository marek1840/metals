package scala.meta.internal.metals

import ch.epfl.scala.bsp4j.RunResult

/**
 * Commands issued by code lenses
 */
object CodeLensCommands {
  val RunCode = Command[RunResult](
    "code-run",
    "run",
    "runs code", // TODO,
    "`string`, source file path"
  )
}
