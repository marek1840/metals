package scala.meta.internal.metals.debug

import java.io.Closeable
import java.net.Socket
import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}
import java.util.concurrent.{ExecutorService, TimeUnit}

import org.eclipse.lsp4j.debug.ConfigurationDoneArguments
import org.eclipse.lsp4j.debug.services.{
  IDebugProtocolClient,
  IDebugProtocolServer
}
import org.eclipse.lsp4j.jsonrpc.MessageConsumer
import org.eclipse.lsp4j.jsonrpc.messages.Message

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}
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

    override def server: IDebugProtocolServer = connection.get()

    def reconnect(implicit ec: ExecutionContext): Future[Unit] = {
      if (isClosed.get()) {
        Future.failed(new IllegalStateException("Remote server is closed"))
      } else {
        connect()
          .onTimeout(10, TimeUnit.SECONDS)(this.cancel())
          .map { newConnection =>
            val oldConnection = connection.getAndSet(newConnection)

            oldConnection.cancel() // TODO what to do when this fails?

            // TODO explain that cancel could occur in the meantime
            if (isClosed.get()) newConnection.cancel()
            else newConnection.start()
          }
      }
    }

    override def cancel(): Unit = {
      if (isClosed.compareAndSet(false, true)) {
        connection.get().cancel()
      }
    }
  }

  trait RemoteConnection extends Cancelable {
    private val isOpen = new AtomicBoolean(false)

    protected def listen(): Unit

    final def start(): Unit = {
      if (isOpen.compareAndSet(false, true)) {
        listen()
      }
    }
  }

  trait ServerConnection extends RemoteConnection with ServerProxy

  object ServerConnection {
    def open(
        socket: Socket,
        service: IDebugProtocolClient,
        configuration: Configuration
    )(
        implicit es: ExecutorService
    ): ServerConnection = {
      var configurationReproducer: ConfigurationReproducer = null

      val launcher = DebugProtocolProxy
        .builder[IDebugProtocolServer](socket, service)
        .traceMessages(GlobalTrace.setup("DAP-client"))
        .wrapMessages(next => {
          val recorder = new ConfigurationRecorder(next, configuration)
          configurationReproducer = recorder
          recorder
        })
        .create()

      new ServerConnection {
        override val server: IDebugProtocolServer = launcher.getRemoteProxy
        override def cancel(): Unit = socket.close()
        override def listen(): Unit = {
          launcher.startListening()
          // TODO explain
          configurationReproducer.replay()
        }
      }
    }
  }

  trait ClientConnection extends RemoteConnection with ClientProxy

  object ClientConnection {
    def open(socket: Socket, service: IDebugProtocolServer)(
        implicit es: ExecutorService
    ): ClientConnection = {
      val launcher = DebugProtocolProxy
        .builder[IDebugProtocolClient](socket, service)
        .traceMessages(GlobalTrace.setup("DAP-client"))
        .create()

      new ClientConnection {
        override val client: IDebugProtocolClient = launcher.getRemoteProxy
        override def cancel(): Unit = socket.close()
        override def listen(): Unit = launcher.startListening()
      }
    }
  }

}
