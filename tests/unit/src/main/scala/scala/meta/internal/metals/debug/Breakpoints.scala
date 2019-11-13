package scala.meta.internal.metals.debug
import org.eclipse.lsp4j.debug.Breakpoint
import org.eclipse.lsp4j.{debug => dap}
import scala.concurrent.Future
import scala.meta.internal.metals.debug.Debuggee.ThreadId

object Breakpoints {
  type Handler[A] = RemoteServer => Future[A]

  trait Listener {
    def onBreakpointUpdate(breakpoint: Breakpoint): Unit
    def onBreakpointRemove(breakpoint: Breakpoint): Unit

    def onStopped(stackFrame: StackFrame): Future[Unit]
  }

  trait Debuggee {
    def continue(threadId: Long): Future[Unit]
    def stepIn(threadId: Long): Future[Unit]
  }

  trait Step
  object Step {
    final case object Continue extends Step
  }

  final class Foo extends Listener {
    override def onStopped(stackFrame: StackFrame): Future[Step] = {
      ???
    }
  }

  final case class StackFrame(
      threadId: ThreadId,
      info: dap.StackFrame,
      variables: Variables
  )
}
