package scala.meta.internal.metals.debug
import org.eclipse.lsp4j.debug.StackFrame
import scala.concurrent.Future

final class InstrumentedDebuggee {
  private var nextStep: DebuggeeStep = ???

  def onStopped(
      thread: Long,
      frame: StackFrame,
      variables: Variables
  ): Future[BreakpointHandler] = {
    nextStep.verify(frame)

  }
  def onFinish(): Future[Unit] = ???
}
