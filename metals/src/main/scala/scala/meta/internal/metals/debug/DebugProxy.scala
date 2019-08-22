package scala.meta.internal.metals.debug
import java.net.{InetSocketAddress, ServerSocket, Socket, URI}
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}

import org.eclipse.lsp4j.debug._

import scala.concurrent.{
  ExecutionContext,
  ExecutionContextExecutorService,
  Future,
  Promise
}
import scala.meta.internal.metals.Cancelable
import scala.meta.internal.metals.MetalsEnrichments._
import scala.meta.internal.metals.debug.Foo.{
  ClientConnection,
  RemoteServer,
  ServerConnection
}

final class DebugProxy(implicit ec: ExecutionContext)
    extends ClientProxy
    with ServerProxy
    with Cancelable {

  private val isClosed = new AtomicBoolean(false)
  private val isRestarting = new AtomicBoolean(false)
  private val terminated = new AtomicReference(Promise[Unit]())

  // actual endpoints. Set in the [[DebugProxy.apply]]
  protected var client: ClientConnection = _
  protected var server: RemoteServer = _

  override def initialize(
      args: InitializeRequestArguments
  ): CompletableFuture[Capabilities] = {
    super.initialize(args).thenApply { capabilities =>
      capabilities.setSupportsRestartRequest(true)
      capabilities
    }
  }

  // TODO comment why synchronized (only one restart at a time)
  // TODO explain isRestarting in context of the notifications send from server
  override def restart(args: RestartArguments): CompletableFuture[Void] = {
    // vscode refreshes the debug console immediately,
    // leading to stale output being printed there
    client.disableOutput()
    // TODO explain
    isRestarting.set(true)

    val restarted = for {
      _ <- disconnected()
      _ <- terminated.get().future
      _ <- server.reconnect
    } yield {
      // TODO explain resetting the state
      terminated.set(Promise())
      isRestarting.set(false)
      client.enableOutput()
    }

    restarted.voided
  }

  // TODO explain terminated promise?
  // TODO explain isRestarting condition
  override def terminated(args: TerminatedEventArguments): Unit = {
    try {
      if (!isRestarting.get()) {
        super.terminated(args)
      }
    } finally {
      terminated.get().success(())
    }
  }

  def cancel(): Unit = {
    if (isClosed.compareAndSet(false, true)) {
      val terminated =
        for {
          _ <- disconnected()
          _ <- this.terminated.get().future
        } yield ()

      terminated
        .withTimeout(10, SECONDS)
        .onComplete(_ => Cancelable.cancelAll(server, client))
    }
  }

  private def disconnected(): Future[Void] = {
    val args = new DisconnectArguments
    args.setTerminateDebuggee(true)
    server.disconnect(args).asScala
  }
}

object DebugProxy {
  def create(
      proxyServer: ServerSocket,
      debugSession: () => Future[URI]
  )(implicit ec: ExecutionContextExecutorService): Future[DebugProxy] = {
    val proxy = new DebugProxy()

    val connectToServer = () => {
      debugSession().map { uri =>
        val socket = new Socket()
        val address = new InetSocketAddress(uri.getHost, uri.getPort)
        val timeout = SECONDS.toMillis(10)
        socket.connect(address, timeout.toInt)

        ServerConnection.open(socket, proxy)
      }
    }

    val awaitClientConnection = () => {
      Future(proxyServer.accept()).map(ClientConnection.open(_, proxy))
    }

    for {
      client <- awaitClientConnection()
      initialServerConnection <- connectToServer()
      server = new RemoteServer(connectToServer, initialServerConnection)
    } yield {
      proxy.client = client
      proxy.server = server

      initialServerConnection.start()
      client.start()

      proxy
    }
  }
}
