package scala.meta.internal.metals.debug
import java.net.{InetSocketAddress, ServerSocket, Socket, URI}
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}
import java.util.concurrent.{CompletableFuture, TimeUnit}

import com.google.gson.{Gson, JsonObject}
import org.eclipse.lsp4j.debug._

import scala.concurrent.{
  ExecutionContext,
  ExecutionContextExecutorService,
  Future,
  Promise
}
import scala.meta.internal.metals.Cancelable
import scala.meta.internal.metals.MetalsEnrichments._
import scala.util.{Failure, Try}

final class DebugAdapterProxy(implicit ec: ExecutionContext)
    extends ClientProxy
    with ServerProxy
    with Cancelable {

  /**
   * Prevents sending any notifications to the client while restarting.
   * We *must* not send the terminate event and sending output may
   * produce some unexpected results (i.e. vscode may print a log
   * from the previous session after clearing the output window)
   */
  private val isRestarting = new AtomicBoolean(false)
  private val isDisconnecting = new AtomicBoolean(false)
  private val isClosed = new AtomicBoolean(false)

  private val terminated = Promise[Unit]()
  private val exited = Promise[Unit]

  private val clientRef = new AtomicReference[ClientConnection]()
  private val serverRef = new AtomicReference[RemoteServer]()

  override def server: RemoteServer = {
    if (isClosed.get()) throw new IllegalStateException("Proxy is closed")
    else serverRef.get()
  }

  override def client: ClientConnection = {
    if (isClosed.get()) throw new IllegalStateException("Proxy is closed")
    else if (isRestarting.get()) ClientConnection.BlackHole
    else clientRef.get()
  }

  def setServer(server: RemoteServer): Unit = {
    if (this.server == null) {
      serverRef.set(server)
      server.listening.onComplete { _ =>
        debugging
          .withTimeout(5, TimeUnit.SECONDS)
          .onComplete(_ => client.cancel())
      }
    } else {
      throw new IllegalStateException("Server already initialized")
    }
  }

  def setClient(client: ClientConnection): Unit = {
    if (this.client == null) {
      clientRef.set(client)
      client.listening.onComplete(_ => this.cancel())
    }
  }

  override def initialize(
      args: InitializeRequestArguments
  ): CompletableFuture[Capabilities] = {
    super.initialize(args).thenApply { capabilities =>
      capabilities.setSupportsRestartRequest(true)
      capabilities
    }
  }

  override def restart(args: RestartArguments): CompletableFuture[Void] = {
    isRestarting.set(true)

    val restarted = server.reconnect.andThen {
      case _ => isRestarting.set(false)
    }

    restarted.voided
  }

  /**
   * doesn't get sent during restarting (see [[isRestarting]] and [[server]]),
   * because it would mean that the debugging has finished
   */
  override def terminated(args: TerminatedEventArguments): Unit = {
    try super.terminated(args)
    finally terminated.success(())
  }

  /**
   * doesn't get sent during restarting (see [[isRestarting]] and [[server]]).
   */
  override def exited(args: ExitedEventArguments): Unit = {
    try super.exited(args)
    finally exited.success(())
  }

  override def disconnect(
      args: DisconnectArguments
  ): CompletableFuture[Void] = {
    isDisconnecting.set(true)
    super.disconnect(args)
  }

  /**
   * Tries to gracefully terminate the connection within time limit.
   */
  def cancel(): Unit = {
    if (isClosed.compareAndSet(false, true)) {
      disconnect()
        .flatMap(_ => debugging)
        .withTimeout(10, SECONDS)
        .onComplete(_ => Cancelable.cancelAll(server, client))
    }
  }

  private def disconnect(): Future[Void] = {
    val args = new DisconnectArguments
    args.setTerminateDebuggee(true)
    server.disconnect(args).asScala
  }

  /**
   * When disconnecting, debugging is considered finished when terminated event is received
   * because the VM may not produce the exited event in this case
   */
  private def debugging: Future[Unit] = {
    val requiredEvents =
      if (isDisconnecting.get()) List(terminated)
      else List(exited, terminated)

    Future.sequence(requiredEvents.map(_.future)).ignoreValue
  }
}

object DebugAdapterProxy {
  def create(
      proxyServer: ServerSocket,
      debugSession: () => Future[URI]
  )(implicit ec: ExecutionContextExecutorService): Future[DebugAdapterProxy] = {
    val awaitingClient = Promise[DebugAdapterProxy]()
    val proxy = new DebugAdapterProxy()

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
      .map(proxy.setServer) // server must be set before client is connected
      .flatMap(_ => awaitClientConnection())
      .map(proxy.setClient)

    awaitingClient.future
  }

  private val gson = new Gson()
  import ch.epfl.scala.{bsp4j => b}
//  def parseParameters(arguments: Seq[Any]): Try[b.DebugSessionParams] =
//    arguments match {
//      case Seq(params: JsonObject) =>
//        Try(gson.fromJson(params, classOf[b.DebugSessionParams]))
//      case Seq(params: b.DebugSessionParams) =>
//        Try(params)
//      case _ =>
//        Failure(new IllegalArgumentException(s"arguments: $arguments"))
//    }
}
