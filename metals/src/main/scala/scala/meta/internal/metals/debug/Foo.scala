package scala.meta.internal.metals.debug

import java.net.Socket
import java.util
import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}
import java.util.concurrent.{
  CompletableFuture,
  ExecutorService,
  TimeUnit,
  Future => JFuture
}

import org.eclipse.lsp4j.debug.services.{
  IDebugProtocolClient,
  IDebugProtocolServer
}
import org.eclipse.lsp4j.debug.{Capabilities, InitializeRequestArguments}
import org.eclipse.lsp4j.jsonrpc.{Launcher, MessageConsumer}
import org.eclipse.lsp4j.jsonrpc.debug.messages.DebugNotificationMessage
import org.eclipse.lsp4j.jsonrpc.messages.Message

import scala.concurrent.{
  ExecutionContext,
  ExecutionContextExecutorService,
  Future
}
import scala.meta.internal.metals.MetalsEnrichments._
import scala.meta.internal.metals.{Cancelable, GlobalTrace}
object Foo {
  final class RemoteServer(
      connect: () => Future[ServerConnection],
      initialConnection: ServerConnection
  ) extends ServerProxy
      with Cancelable {
    private val isClosed = new AtomicBoolean(false)
    private val connection = new AtomicReference(initialConnection)

    override def server: ServerConnection = connection.get()

    def reconnect(implicit ec: ExecutionContext): Future[Unit] = {
      if (isClosed.get()) {
        Future.failed(new IllegalStateException("Remote server is closed"))
      } else {
        connect()
          .onTimeout(10, TimeUnit.SECONDS)(this.cancel())
          .flatMap { newConnection =>
            val oldConnection = connection.getAndSet(newConnection)

            oldConnection.cancel() // TODO what to do when this fails?

            // TODO explain that cancel could occur in the meantime
            if (isClosed.get()) {
              newConnection.cancel()
              Future.failed(
                new IllegalStateException("Remote server is closed")
              )
            } else {
              newConnection.start()
              for {
                _ <- newConnection.initialize(config.get()).asScala
                _ <- newConnection.launch(util.Collections.emptyMap()).asScala
              } yield ()
            }
          }
      }
    }

    private val config = new AtomicReference[InitializeRequestArguments]()
    override def initialize(
        args: InitializeRequestArguments
    ): CompletableFuture[Capabilities] = {
      try super.initialize(args)
      finally config.set(args)
    }

    override def cancel(): Unit = {
      if (isClosed.compareAndSet(false, true)) {
        server.cancel()
      }
    }
  }

  trait RemoteConnection extends Cancelable {
    private lazy val listening: JFuture[Void] = launcher.startListening()

    protected def launcher: Launcher[_]
    protected def socket: Socket

    final def start(): Unit = listening

    override final def cancel(): Unit = {
      listening.cancel(true)
      socket.close()
    }
  }

  final class ServerConnection(
      val launcher: Launcher[IDebugProtocolServer],
      val socket: Socket
  ) extends RemoteConnection
      with ServerProxy {
    override val server: IDebugProtocolServer = launcher.getRemoteProxy
  }

  object ServerConnection {
    def open(socket: Socket, service: IDebugProtocolClient)(
        implicit es: ExecutionContextExecutorService
    ): ServerConnection = {
      val launcher = DebugProtocolProxy
        .builder[IDebugProtocolServer](socket, service)
        .traceMessages(GlobalTrace.setup("dap-server"))
        .create()

      new ServerConnection(launcher, socket)
    }
  }

  final class ClientConnection(
      val launcher: Launcher[IDebugProtocolClient],
      val socket: Socket,
      shouldSendOutput: AtomicBoolean
  ) extends RemoteConnection
      with ClientProxy {
    override val client: IDebugProtocolClient = launcher.getRemoteProxy

    def disableOutput(): Unit = shouldSendOutput.set(false)
    def enableOutput(): Unit = shouldSendOutput.set(true)
  }

  object ClientConnection {
    def open(socket: Socket, service: IDebugProtocolServer)(
        implicit es: ExecutorService
    ): ClientConnection = {
      val shouldSendOutput = new AtomicBoolean(true)

      val launcher = DebugProtocolProxy
        .builder[IDebugProtocolClient](socket, service)
        .traceMessages(GlobalTrace.setup("dap-client"))
        .wrapMessages(
          new ToggleableOutputConsumer(() => shouldSendOutput.get(), _)
        )
        .create()

      new ClientConnection(launcher, socket, shouldSendOutput)
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
  }

  object OutputNotification {
    def unapply(message: Message): Boolean = message match {
      case notification: DebugNotificationMessage =>
        notification.getMethod == "output"
      case _ =>
        false
    }
  }
}
