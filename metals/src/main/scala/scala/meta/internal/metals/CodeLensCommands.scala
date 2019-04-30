package scala.meta.internal.metals

import ch.epfl.scala.{bsp4j => b}
import scala.meta.internal.metals.CommandArguments.StringArg
import scala.meta.io.AbsolutePath
import scala.meta.internal.metals.MetalsEnrichments._

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

  case class RunCodeArgs(file: AbsolutePath, mainClass: String)

  case object RunCodeArgs {
    def unapply(jsonArgs: List[AnyRef]): Option[RunCodeArgs] =
      jsonArgs match {
        case StringArg(filepath) :: StringArg(mainClass) :: _ =>
          Some(RunCodeArgs(filepath.toAbsolutePath, mainClass))
        case _ => None
      }
  }

  val all = List(
    RunCode
  )
}
