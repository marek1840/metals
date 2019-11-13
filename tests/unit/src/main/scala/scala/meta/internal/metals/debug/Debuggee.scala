package scala.meta.internal.metals.debug
import org.eclipse.lsp4j.debug.ContinueArguments
import org.eclipse.lsp4j.debug.ContinueResponse
import org.eclipse.lsp4j.debug.ScopesArguments
import org.eclipse.lsp4j.debug.ScopesResponse
import org.eclipse.lsp4j.debug.StackTraceArguments
import org.eclipse.lsp4j.debug.StackTraceResponse
import org.eclipse.lsp4j.debug.VariablesArguments
import org.eclipse.lsp4j.debug.VariablesResponse
import scala.concurrent.Future

import scala.meta.internal.metals.MetalsEnrichments._

object Debuggee {
  type ThreadId = Long
}

final class Debuggee(server: RemoteServer) {
  def stackTrace(
      thread: Long,
      depth: Long = 0
  ): Future[StackTraceResponse] = {
    val args = new StackTraceArguments
    args.setThreadId(thread)
    args.setLevels(depth)
    server.stackTrace(args).asScala
  }

  def scopes(frame: Long): Future[ScopesResponse] = {
    val args = new ScopesArguments
    args.setFrameId(frame)
    server.scopes(args).asScala
  }

  def variables(id: Long): Future[VariablesResponse] = {
    val args = new VariablesArguments
    args.setVariablesReference(id)
    server.variables(args).asScala
  }

  def continue(threadId: Long): Future[ContinueResponse] = {
    val continueArgs = new ContinueArguments
    continueArgs.setThreadId(threadId)
    server.continue_(continueArgs).asScala
  }
}
