package scala.meta.internal.metals.debug
import org.eclipse.lsp4j.debug.Breakpoint
import scala.collection.concurrent.TrieMap
import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

final class BreakpointCollector(val server: RemoteServer)(
    implicit val ec: ExecutionContext
) extends BreakpointHandler {
  private val hits = TrieMap.empty[Breakpoint, mutable.Buffer[Variables]]

  def apply(): collection.Map[Breakpoint, Seq[Variables]] = {
    hits.mapValues(_.toSeq)
  }

  def onStopped(threadId: Long): Future[Unit] = {
    for {
      (breakpoint, hit) <- hit(threadId)
      _ <- continue(threadId)
    } yield {
      val buffer = hits.getOrElseUpdate(breakpoint, mutable.Buffer.empty)
      buffer += hit
    }
  }

  override def clear(): Unit = {
    super.clear()
    hits.clear()
  }
}
