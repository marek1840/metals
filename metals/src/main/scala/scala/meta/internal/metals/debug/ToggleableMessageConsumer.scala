package scala.meta.internal.metals.debug
import java.util.concurrent.atomic.AtomicBoolean

import org.eclipse.lsp4j.jsonrpc.MessageConsumer
import org.eclipse.lsp4j.jsonrpc.debug.messages.DebugResponseMessage
import org.eclipse.lsp4j.jsonrpc.messages.Message

object ToggleableMessageConsumer {

  /**
   * When closed, blocks all messages
   */
  def forClient(isClosed: AtomicBoolean): MessageConsumer => MessageConsumer = {
    consumer =>
      { message =>
        if (isClosed.get()) {
          scribe.info(message.toString)
        } else {
          consumer.consume(message)
        }
      }
  }

  /**
   * When closed, blocks all messages but a response to disconnect
   */
  def forServer(isClosed: AtomicBoolean): MessageConsumer => MessageConsumer = {
    consumer =>
      {
        case message: DebugResponseMessage if isClosed.get() =>
          consumer.consume(message)
        case message if isClosed.get() =>
          scribe.info(message.toString)
        case message =>
          consumer.consume(message)
      }
  }
}
