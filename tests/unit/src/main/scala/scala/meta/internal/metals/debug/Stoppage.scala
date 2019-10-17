package scala.meta.internal.metals.debug

import org.eclipse.lsp4j.{debug => dap}
import scala.concurrent.Future

final case class Stoppage(frame: StackFrame, cause: Stoppage.Cause)

object Stoppage {

  sealed trait Cause
  object Cause {
    case class Breakpoint(info: dap.Breakpoint) extends Cause
    case object Step extends Cause
  }

  trait Handler {
    def apply(stoppage: Stoppage): Future[DebugStep]
  }

  object Handler {
    val Continue: Handler = _ => Future.successful(DebugStep.Continue)
  }
}
