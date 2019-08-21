package scala.meta.internal.metals.debug
import java.net.{InetSocketAddress, ServerSocket, Socket, URI}
import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}
import java.util.concurrent.{CompletableFuture, TimeUnit}

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

abstract class DebugProxy(implicit ec: ExecutionContext)
    extends ClientProxy
    with ServerProxy
    with Cancelable {
  private val isClosed = new AtomicBoolean(false)
  private val isRestarting = new AtomicBoolean(false)

  /**
   * Promise fulfilled once the debug adapter is fully configured.
   * Used in restarting to signal that the reconnected server is set up
   */
  private val configured = new AtomicReference(Promise[Unit]())

  override protected def client: ClientConnection
  override protected def server: RemoteServer

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
  override def restart(args: RestartArguments): CompletableFuture[Void] =
    synchronized {
      // TODO explain
      isRestarting.set(true)

      val restarted = for {
        _ <- disconnected()
        _ <- terminated.get().future
        _ <- server.reconnect
        _ <- configurationDone(new ConfigurationDoneArguments).asScala
      } yield {
        // TODO explain resetting the state
        terminated.set(Promise())
        isRestarting.set(false)
      }

      restarted.voided
    }

  private val terminated = new AtomicReference(Promise[Unit]())

  // TODO explain terminated promise?
  // TODO explain isRestarting condition
  override def terminated(args: TerminatedEventArguments): Unit = {
    try {
      if (!isRestarting.get()) {
        super.terminated(args)
      }
    } finally {
      terminated.get().success(())
//      val promise = terminated.getAndSet(Promise())
//      promise.success(())
    }
  }

  override def configurationDone(
      args: ConfigurationDoneArguments
  ): CompletableFuture[Void] = {
    try super.configurationDone(args)
    finally {
//      configured.get().success(())
    }
  }

  def cancel(): Unit = {
    if (isClosed.compareAndSet(false, true)) {
      for {
        _ <- disconnected()
        _ <- this.terminated.get().future
      } yield {
        Cancelable.cancelAll(server, client)
      }
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
    def connectToServer(): Future[Socket] = {
      debugSession().map { uri =>
        val socket = new Socket()
        val address = new InetSocketAddress(uri.getHost, uri.getPort)
        val timeout = TimeUnit.SECONDS.toMillis(10)
        socket.connect(address, timeout.toInt)

        socket
      }
    }

    DebugProxy(
      () => Future(proxyServer.accept()),
      connectToServer
    )
  }

  def apply(
      clientConnection: () => Future[Socket],
      serverConnection: () => Future[Socket]
  )(implicit ec: ExecutionContextExecutorService): Future[DebugProxy] = {

    val clientRef = new AtomicReference[ClientConnection]()
    val serverRef = new AtomicReference[RemoteServer]()

    val proxy = new DebugProxy {
      override def client: ClientConnection = clientRef.get()
      override def server: RemoteServer = serverRef.get()
    }

    val configuration = new Configuration
    def connectToServer(): Future[ServerConnection] =
      serverConnection().map(ServerConnection.open(_, proxy, configuration))

    for {
      client <- clientConnection().map(ClientConnection.open(_, proxy))
      initialServerConnection <- connectToServer()
      server = new RemoteServer(connectToServer, initialServerConnection)
    } yield {
      clientRef.set(client)
      initialServerConnection.start()

      // client initializes communication, so it must be started last
      serverRef.set(server)
      client.start()

      proxy
    }
  }
}
