package scala.meta.internal.metals.debug
import java.net.{InetSocketAddress, ServerSocket, Socket, URI}
import java.util
import java.util.concurrent.{CompletableFuture, TimeUnit}
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}

import org.eclipse.lsp4j.debug._

import scala.collection.mutable
import scala.concurrent.{
  ExecutionContext,
  ExecutionContextExecutorService,
  Future,
  Promise
}
import scala.meta.internal.metals.Cancelable
import scala.meta.internal.metals.MetalsEnrichments._

final class DebugProxy(implicit ec: ExecutionContext)
    extends ClientProxy
    with ServerProxy
    with Cancelable {
  private val isClosed = new AtomicBoolean(false)
  private val isRestarting = new AtomicBoolean(false)
  private val terminated = new AtomicReference(Promise[Unit]())
  private val exited = new AtomicReference(Promise[Unit]())

  // actual endpoints. Set in the [[DebugProxy.apply]]
  protected var client: ClientConnection = _
  protected var server: RemoteServer = _

  def setServer(server: RemoteServer): Unit = {
    if (this.server == null) {
      this.server = server
      server.listening.onComplete { _ =>
        val requiredEvents = List(exited, terminated).map(_.get().future)
        Future
          .sequence(requiredEvents)
          .withTimeout(5, TimeUnit.SECONDS)
          .onComplete(_ => {
            client.cancel()
          })
      }
    } else {
      throw new IllegalStateException("Server already initialized")
    }
  }

  override def output(args: OutputEventArguments): Unit = {
    super.output(args)
  }

  def setClient(client: ClientConnection): Unit = {
    if (this.client == null) {
      this.client = client
      client.listening.onComplete(_ => {
        this.cancel()
      })
    }
  }

  def start(client: ClientConnection, server: RemoteServer): Unit = {}

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
    // leading to stale output being printed
    client.disableOutput()
    // TODO explain
    isRestarting.set(true)

    val restarted = for {
      _ <- disconnect()
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

  private val isTerminating = new AtomicBoolean(false)
  def terminateGracefully(): Unit = {
//    if (isTerminating.compareAndSet(false, true)) {
//      java.lang.Thread.sleep(10000)
//      val requiredEvents = List(exited, terminated).map(_.get().future)
//      Future
//        .sequence(requiredEvents)
//        .withTimeout(3, TimeUnit.SECONDS)
//        .onComplete(_ => {
//          client.cancel()
//        })
//    }
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

  override def exited(args: ExitedEventArguments): Unit = {
    try {
      if (!isRestarting.get()) {
        super.exited(args)
//        terminateGracefully()
      }
    } finally {
      exited.get().success(())

    }
  }

  def cancel(): Unit = {
    if (isClosed.compareAndSet(false, true)) {
      disconnect()
        .map(_ => List(exited, terminated).map(_.get().future))
        .flatMap(Future.sequence(_))
        .withTimeout(10, SECONDS)
        .onComplete(_ => Cancelable.cancelAll(server, client))
    }
  }

  private def disconnect(): Future[Void] = {
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
    val awaitingClient = Promise[DebugProxy]()
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
      awaitingClient.success(proxy)
      Future(proxyServer.accept()).map(ClientConnection.open(_, proxy))
    }

    connectToServer()
      .map(new RemoteServer(connectToServer, _))
      .map(proxy.setServer)
      .flatMap(_ => awaitClientConnection())
      .map(proxy.setClient)

    awaitingClient.future
  }
}
