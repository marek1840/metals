package scala.meta.internal.metals.debug
import java.util.concurrent.atomic.AtomicInteger
import org.eclipse.lsp4j.jsonrpc.messages.IdentifiableMessage
import org.eclipse.lsp4j.jsonrpc.messages.Message
import scala.collection.mutable
import scala.meta.internal.metals.debug.MessageIdAdapter.FirstMessageId

final class MessageIdAdapter {
  private val serverMessageCounter = new AtomicInteger(FirstMessageId)
  private val clientMessageCounter = new AtomicInteger(FirstMessageId)

  private val sequence = mutable.Map.empty[Int, Int]

  def nextServerId: Int = serverMessageCounter.getAndIncrement()
  def nextClientId: Int = serverMessageCounter.getAndIncrement()

  def adaptMessageFromClient: Message => Unit = {
    case message: IdentifiableMessage if message.getId == null =>
      message.setId(nextServerId)
    case message: IdentifiableMessage if message.getRawId.isRight =>
      val originalId = message.getId.toInt
      val newId = nextServerId
      sequence += (newId -> originalId)
      message.setId(newId)
    case _ =>
  }

  def adaptMessageFromServer: Message => Unit = {
    case message: IdentifiableMessage if message.getId == null =>
      message.setId(clientMessageCounter.getAndIncrement())
    case message: IdentifiableMessage if message.getRawId.isRight =>
      sequence.remove(message.getId.toInt) match {
        case Some(originalId) =>
          message.setId(originalId)
        case None =>
          message.setId(null)
      }

    case _ =>
  }

  val adaptServerMessage: Message => Message = {
    case message: IdentifiableMessage if message.getId == null =>
      message.setId(clientMessageCounter.getAndIncrement())
      message
    case message: IdentifiableMessage if message.getRawId.isRight =>
      sequence.remove(message.getId.toInt) match {
        case Some(previousId) =>
          message.setId(previousId)
        case None =>
          message.setId(null)
      }
      message
    case message =>
      message
  }
}

object MessageIdAdapter {
  protected val FirstMessageId = 1
}
