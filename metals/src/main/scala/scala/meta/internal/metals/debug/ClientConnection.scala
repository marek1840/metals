package scala.meta.internal.metals.debug

import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

import org.eclipse.lsp4j.debug.services.{
  IDebugProtocolClient,
  IDebugProtocolServer
}
import org.eclipse.lsp4j.jsonrpc.MessageConsumer
import org.eclipse.lsp4j.jsonrpc.debug.messages.DebugNotificationMessage
import org.eclipse.lsp4j.jsonrpc.messages.Message

import scala.concurrent.{ExecutionContext, ExecutionContextExecutorService}
import scala.meta.internal.metals.{CancelableFuture, GlobalTrace}

final class ClientConnection(
    val connection: CancelableFuture[Unit],
    val client: IDebugProtocolClient,
    shouldSendOutput: AtomicBoolean
)(implicit val ec: ExecutionContext)
    extends RemoteConnection
    with ClientProxy {
  def disableOutput(): Unit = shouldSendOutput.set(false)
  def enableOutput(): Unit = shouldSendOutput.set(true)
}

object ClientConnection {
  def open(socket: Socket, service: IDebugProtocolServer)(
      implicit es: ExecutionContextExecutorService
  ): ClientConnection = {
    val shouldSendOutput = new AtomicBoolean(true)

    val builder = DebugProtocolProxy
      .builder[IDebugProtocolClient](socket, service)
      .traceMessages(GlobalTrace.setup("dap-client"))
      .wrapMessages { next =>
        new ToggleableOutputConsumer(() => shouldSendOutput.get(), next)
      }

    val launcher = builder.create()
    val connection = RemoteConnection.start(launcher, socket)
    new ClientConnection(connection, launcher.getRemoteProxy, shouldSendOutput)
  }

  private final class ToggleableOutputConsumer(
      shouldSendOutput: () => Boolean,
      next: MessageConsumer
  ) extends MessageConsumer {
    override def consume(message: Message): Unit = {
      message match {
        case OutputNotification() if shouldSendOutput() =>
          next.consume(message)
        case _ =>
          next.consume(message)
      }
    }
  }

  private object OutputNotification {
    def unapply(message: Message): Boolean = message match {
      case notification: DebugNotificationMessage =>
        notification.getMethod == "output"
      case _ =>
        false
    }
  }
}
