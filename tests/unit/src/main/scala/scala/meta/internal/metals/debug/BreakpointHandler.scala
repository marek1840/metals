package scala.meta.internal.metals.debug
import org.eclipse.lsp4j.debug.Breakpoint
import org.eclipse.lsp4j.debug.ContinueArguments
import org.eclipse.lsp4j.debug.ContinueResponse
import org.eclipse.lsp4j.debug.ScopesArguments
import org.eclipse.lsp4j.debug.ScopesResponse
import org.eclipse.lsp4j.debug.StackFrame
import org.eclipse.lsp4j.debug.StackTraceArguments
import org.eclipse.lsp4j.debug.StackTraceResponse
import org.eclipse.lsp4j.debug.VariablesArguments
import org.eclipse.lsp4j.debug.VariablesResponse
import scala.collection.concurrent.TrieMap
import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.meta.internal.metals.MetalsEnrichments._

abstract class BreakpointHandler {
  private val breakpoints = TrieMap.empty[Long, Breakpoint]

  protected def server: RemoteServer
  implicit protected def ec: ExecutionContext

  def onStopped(threadId: Long): Future[Unit]

  protected[debug] final def update(breakpoint: Breakpoint): Unit = {
    breakpoints.put(breakpoint.getId, breakpoint)
  }

  protected[debug] final def remove(breakpoint: Breakpoint): Unit = {
    breakpoints.remove(breakpoint.getId)
  }

  final def stackTrace(
      thread: Long,
      depth: Long = 0
  ): Future[StackTraceResponse] = {
    val args = new StackTraceArguments
    args.setThreadId(thread)
    args.setLevels(depth)
    server.stackTrace(args).asScala
  }

  final def scopes(frame: Long): Future[ScopesResponse] = {
    val args = new ScopesArguments
    args.setFrameId(frame)
    server.scopes(args).asScala
  }

  final def variables(id: Long): Future[VariablesResponse] = {
    val args = new VariablesArguments
    args.setVariablesReference(id)
    server.variables(args).asScala
  }

  final def continue(threadId: Long): Future[ContinueResponse] = {
    val continueArgs = new ContinueArguments
    continueArgs.setThreadId(threadId)
    server.continue_(continueArgs).asScala
  }

  final def hit(threadId: Long): Future[(Breakpoint, BreakpointHit)] = {
    for {
      frame <- stackTrace(threadId, depth = 1).map(_.getStackFrames.head)
      breakpoint <- findBreakpoint(frame)
      scopes <- scopes(frame.getId).map(_.getScopes).flatMap {
        case scopes if scopes.isEmpty =>
          Future.failed(new IllegalStateException("No variable scopes"))
        case scopes =>
          Future.successful(scopes)
      }
      hit <- {
        val foo = scopes.map { scope =>
          variables(scope.getVariablesReference).map { response =>
            val variables = response.getVariables
              .map(v => Variable(v.getName, v.getType, v.getValue))
              .toList

            scope.getName -> variables
          }
        }
        Future.sequence(foo.toList).map { scopes =>
          BreakpointHit(scopes.toMap)
        }
      }
    } yield (breakpoint, hit)
  }

  final def findBreakpoint(stackFrame: StackFrame): Future[Breakpoint] = {
    breakpoints.values.find(_.getLine == stackFrame.getLine) match {
      case Some(breakpoint) =>
        Future.successful(breakpoint)
      case None =>
        Future.failed(
          new NoSuchElementException(
            s"No breakpoint defined on line ${stackFrame.getLine}"
          )
        )
    }
  }

  def clear(): Unit = {
    breakpoints.clear()
  }
}
