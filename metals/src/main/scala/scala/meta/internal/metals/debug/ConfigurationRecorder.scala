package scala.meta.internal.metals.debug

import java.util.concurrent.atomic.AtomicBoolean

import org.eclipse.lsp4j.jsonrpc.MessageConsumer
import org.eclipse.lsp4j.jsonrpc.debug.messages.DebugRequestMessage
import org.eclipse.lsp4j.jsonrpc.messages.{IdentifiableMessage, Message}

import scala.collection.mutable
import scala.meta.internal.metals.debug.Configuration._

final class Configuration {
  private val recorded = mutable.LinkedHashSet.empty[Message]
  @volatile private var isReplaying = false

  def record(message: Message): Unit = {
    message match {
      case ConfigurationRequest() if !isReplaying =>
        recorded += message
      case LastConfigurationRequest() if isReplaying =>
        isReplaying = false
      case _ =>
      // ignore
    }
  }

  def replay(consumer: MessageConsumer): Unit =
    synchronized {
      // TODO explain condition (otherwise isReplaying would always be true)
      if (recorded.nonEmpty) {
        isReplaying = true
        recorded.foreach(consumer.consume)

        val lastMessage = new DebugRequestMessage()
//        lastMessage.setId(recorded.last.asInstanceOf[IdentifiableMessage].getId.toInt + 1) // id must be set
        lastMessage.setMethod("configurationDone")
        consumer.consume(lastMessage)
      }
    }
}

trait ConfigurationReproducer {
  def replay(): Unit
}

final class ConfigurationRecorder(
    next: MessageConsumer,
    configuration: Configuration
) extends MessageConsumer
    with ConfigurationReproducer {
  @volatile private var reproduced = false
  @volatile private var id = "0"
  // must record after consuming, otherwise last message is recorded and then replayed forever

  override def consume(message: Message): Unit = {
    next.consume(message)
    configuration.record(message)
  }

  // TODO explain synchronized
  def replay(): Unit = synchronized {
    if (!reproduced) {
      configuration.replay(next)
      reproduced = true
    }
  }
}

object Configuration {
  // TODO comment: cannot store configurationDone
  private val configurationMethods =
    Set("initialize", "launch")

  private object ConfigurationRequest {
    def unapply(message: Message): Boolean = {
      message match {
        case request: DebugRequestMessage =>
          configurationMethods.contains(request.getMethod)
        case _ => false
      }
    }
  }

  private object LastConfigurationRequest {
    def unapply(message: Message): Boolean = {
      message match {
        case request: DebugRequestMessage =>
          request.getMethod == "configurationDone"
        case _ =>
          false
      }
    }
  }
}
