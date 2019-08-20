package scala.meta.internal.metals.debug

import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

import org.eclipse.lsp4j.jsonrpc.{Launcher, MessageConsumer}
import org.eclipse.lsp4j.jsonrpc.debug.DebugLauncher
import org.eclipse.lsp4j.jsonrpc.messages.Message

import scala.concurrent.{
  ExecutionContext,
  ExecutionContextExecutorService,
  Future
}
import scala.meta.internal.metals.{Cancelable, GlobalTrace}
import scala.reflect.{ClassTag, classTag}

final case class Remote[A](launcher: Launcher[A], socket: Socket)(
    implicit ec: ExecutionContext
) extends Cancelable {
  private val started = new AtomicBoolean(false)

  val service: A = launcher.getRemoteProxy

  def start(): Unit = {
    if (started.compareAndSet(false, true)) {
      Future(launcher.startListening().get())
        .onComplete(_ => cancel())
    }
  }

  def cancel(): Unit = {
    socket.close()
  }
}

object Remote {
  def jsonRPC[A: ClassTag](
      name: String,
      socket: Socket,
      service: A
  )(implicit executor: ExecutionContextExecutorService): Remote[A] = {
    val launcher = new DebugLauncher.Builder[A]
      .setRemoteInterface(classTag[A].runtimeClass.asInstanceOf[Class[A]])
      .traceMessages(GlobalTrace.setup(name))
      .validateMessages(true)
      .setExecutorService(executor)
      .setInput(socket.getInputStream)
      .setOutput(socket.getOutputStream)
      .setLocalService(service)
      .create()

    Remote(launcher, socket)
  }

  private final class MessageWrapper(
      next: MessageConsumer,
      listener: Message => Unit
  ) extends MessageConsumer {
    override def consume(message: Message): Unit = {
      try next.consume(message)
      finally listener(message)
    }

  }
}
