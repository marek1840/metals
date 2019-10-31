package scala.meta.internal.metals.debug
import org.eclipse.lsp4j.debug.Breakpoint
import scala.collection.concurrent.TrieMap
import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

final class Breakpoints(debugger: TestDebugger)(implicit ec: ExecutionContext) {
  private val breakpoints = TrieMap.empty[Long, Breakpoint]
  private val hits = TrieMap.empty[Breakpoint, mutable.Buffer[BreakpointHit]]

  def update(breakpoint: Breakpoint): Unit = {
    breakpoints.put(breakpoint.getId, breakpoint)
  }

  def remove(breakpoint: Breakpoint): Unit = {
    breakpoints.remove(breakpoint.getId)
  }

  def apply(): collection.Map[Breakpoint, Seq[BreakpointHit]] = {
    hits.mapValues(_.toSeq)
  }

  def onStopped(threadId: Long): Future[Unit] = {
    for {
      stackTrace <- debugger.stackTrace(threadId).flatMap {
        case stackTrace if stackTrace.getTotalFrames == 0 =>
          Future.failed(new IllegalStateException("0 stack frames"))
        case stackTrace =>
          Future.successful(stackTrace.getStackFrames)
      }

      top = stackTrace.head
      breakpoint = breakpoints.values.find(_.getLine == top.getLine)
      scopes <- debugger.scopes(top.getId).map(_.getScopes).flatMap {
        case scopes if scopes.isEmpty =>
          Future.failed(new IllegalStateException("No variable scopes"))
        case scopes =>
          Future.successful(scopes)
      }
      hit <- {
        val foo = scopes.map { scope =>
          debugger.variables(scope.getVariablesReference).map { response =>
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
      _ <- debugger.continue(threadId)
    } yield {
      breakpoint
        .map(id => hits.getOrElseUpdate(id, mutable.Buffer.empty))
        .foreach(buffer => buffer += hit)
    }
  }

  def clear(): Unit = {
    breakpoints.clear()
    hits.clear()
  }
}
