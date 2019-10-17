package scala.meta.internal.metals.debug

import scala.collection.mutable
import scala.concurrent.Future
import scala.meta.internal.metals.debug.Stoppage.Cause
import scala.meta.internal.metals.debug.Stoppage.Handler

final class VariableHoarder extends Handler {
  private val variableBuffer = mutable.Buffer.empty[Variables]

  override def apply(frame: StackFrame, cause: Cause): Future[DebugStep] = {
    variableBuffer += frame.variables
    Future.successful(DebugStep.Continue)
  }

  def variables: List[Variables] = this.variableBuffer.toList
}
