package scala.meta.internal.metals.debug

import java.util.concurrent.atomic.AtomicInteger
import org.eclipse.lsp4j.jsonrpc.MessageConsumer
import org.eclipse.lsp4j.jsonrpc.debug.messages.DebugRequestMessage
import org.eclipse.lsp4j.jsonrpc.debug.messages.DebugResponseMessage
import org.eclipse.lsp4j.jsonrpc.messages.Message
import scala.collection.mutable
import scala.meta.internal.metals.debug.DebugProtocol.FirstMessageId

final class ClientMessageIdAdapter(next: RemoteEndpoint)
    extends RemoteEndpoint {
  private val clientMessageCounter = new AtomicInteger(FirstMessageId)

  def nextClientId: Int = clientMessageCounter.getAndIncrement()

  override def consume(message: Message): Unit = {
    message match {
      case response: DebugResponseMessage =>
        response.setId(nextClientId)
      case _ =>
      // ignore
    }
    next.consume(message)
  }

  override def listen(consumer: MessageConsumer): Unit = {
    next.listen(consumer)
  }

  override def cancel(): Unit = {
    next.cancel()
  }
}
