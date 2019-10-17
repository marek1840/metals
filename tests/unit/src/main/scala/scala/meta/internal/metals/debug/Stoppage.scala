package scala.meta.internal.metals.debug

import org.eclipse.lsp4j.{debug => dap}
import scala.collection.mutable
import scala.concurrent.Future

object Stoppage {
  sealed trait Cause
  object Cause {
    case class Breakpoint(info: dap.Breakpoint) extends Cause
    case object Step extends Cause
  }

  trait Handler {
    def apply(frame: StackFrame, cause: Cause): Future[DebugStep]
  }

  object Handler {
    val Continue: Handler = (_, _) => Future.successful(DebugStep.Continue)
  }

  case class Location(file: String, line: Int)
}
