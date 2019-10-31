package scala.meta.internal.metals.debug
import java.util.concurrent.atomic.AtomicInteger
import org.eclipse.lsp4j.jsonrpc.MessageConsumer
import org.eclipse.lsp4j.jsonrpc.debug.messages.DebugRequestMessage
import org.eclipse.lsp4j.jsonrpc.debug.messages.DebugResponseMessage
import org.eclipse.lsp4j.jsonrpc.messages.IdentifiableMessage
import org.eclipse.lsp4j.jsonrpc.messages.Message
import scala.collection.mutable
import scala.meta.internal.metals.debug.ServerMessageIdAdapter.FirstMessageId

final class ServerMessageIdAdapter(next: RemoteEndpoint)
    extends RemoteEndpoint {
  private val serverMessageCounter = new AtomicInteger(FirstMessageId)
  private val clientMessageCounter = new AtomicInteger(FirstMessageId)

  private val sequence = mutable.Map.empty[Int, Int]

  def nextServerId: Int = serverMessageCounter.getAndIncrement()
  def nextClientId: Int = serverMessageCounter.getAndIncrement()

  override def consume(message: Message): Unit = {
    message match {
      case request: DebugRequestMessage if request.getId == null =>
        request.setId(nextServerId)
      case request: DebugRequestMessage =>
        val originalId = request.getId.toInt
        val newId = nextServerId
        if (originalId != newId) {
          sequence += (newId -> originalId)
          request.setId(newId)
        }
      case _ =>
      // ignore
    }
    next.consume(message)
  }

  override def listen(consumer: MessageConsumer): Unit = {
    next.listen { message =>
      message match {
        case response: DebugResponseMessage =>
          sequence
            .remove(response.getId.toInt)
            .foreach(response.setId)
        case _ =>
        // ignore
      }
      consumer.consume(message)
    }
  }

  override def cancel(): Unit = {
    next.cancel()
  }
}

object ServerMessageIdAdapter {
  protected val FirstMessageId = 1
}
