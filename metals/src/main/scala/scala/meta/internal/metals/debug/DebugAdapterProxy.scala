package scala.meta.internal.metals.debug
import java.net.{ServerSocket, Socket, URI}
import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.{CompletableFuture, CountDownLatch, TimeUnit}

import ch.epfl.scala.bsp4j.DebugSessionParams
import ch.epfl.scala.{bsp4j => b}
import com.google.gson.{Gson, JsonObject}
import org.eclipse.lsp4j.debug._
import org.eclipse.lsp4j.debug.services.{
  IDebugProtocolClient,
  IDebugProtocolServer
}

import scala.concurrent.{ExecutionContextExecutorService, Future, Promise}
import scala.meta.internal.metals.MetalsEnrichments._
import scala.meta.internal.metals.{
  BuildServerConnection,
  Cancelable,
  MutableCancelable,
  Remote
}
import scala.reflect.ClassTag
import scala.util.{Failure, Try}

class DebugAdapterProxy(
    connectToServer: DebugAdapterProxy => Future[Remote[ServerEndpoint]],
    connectToClient: DebugAdapterProxy => Future[Remote[ClientEndpoint]]
)(
    implicit val ec: ExecutionContextExecutorService
) extends ClientProxy
    with ServerProxy
    with Cancelable {

  /**
   * TODO
   */
  private var terminated = Promise[Unit]()
  private var cancelables = new MutableCancelable() // TODO clean somehow

  protected var client: IDebugProtocolClient = _
  protected var server: IDebugProtocolServer = _

  private def connectToServer(): Future[Unit] = {
    // TODO lock until connected

    val disconnected = if (server == null) {
      Future.successful(())
    } else {
      val args = new DisconnectArguments
      args.setTerminateDebuggee(true)
      server.disconnect(args).asScala
    }

    val connection: Future[Unit] = for {
      _ <- disconnected
      server <- connectToServer(this)
    } yield {
      cancelables = new MutableCancelable()
      this.server = server.service
      cancelables.add(server)
      server.start()
    }

    connection
      .onTimeout(30, TimeUnit.SECONDS)(this.cancel())
  }

  private def connectToClient(): Future[Unit] = {
    // TODO lock until connected

    val disconnected = if (client == null) {
      Future.successful(())
    } else {
      terminated.future
    }

    val connection: Future[Unit] = for {
      _ <- disconnected
      client <- connectToClient(this)
    } yield {
      this.client = client.service
      cancelables.add(client)
      terminated = Promise()
      client.start()
    }

    connection
      .onTimeout(30, TimeUnit.SECONDS)(this.cancel())
  }

  /**
   * Connecting to client must happen last, so that we can safely
   * forward the requests to the already connected server
   */
  private[DebugAdapterProxy] def connect(): Future[Unit] = {
    for {
      _ <- connectToServer()
      _ <- connectToClient()
    } yield ()
  }

  override def disconnect(
      args: DisconnectArguments
  ): CompletableFuture[Void] = {
    if (true) { // TODO fix pretending it is always a restart {
      val disconnectedServer = connectToServer()
      disconnectedServer.foreach(_ => connectToClient())
      disconnectedServer.voided
    } else {
      super.disconnect(args)
    }
  }

  // TODO if restarting, set flag to true
  override def terminated(args: TerminatedEventArguments): Unit = {
    args.setRestart(true)
    try super.terminated(args)
    finally terminated.success(())
  }

  def cancel(): Unit = {
    val args = new DisconnectArguments()
    args.setTerminateDebuggee(true)

    val communicationDone = for {
      _ <- server.disconnect(args).asScala
      _ <- terminated.future.withTimeout(30, TimeUnit.SECONDS)
    } yield ()

    // close the communication even if client or server weren't closed
    communicationDone.onComplete(_ => cancelables.cancel())
  }
}

object DebugAdapterProxy {
  final case class DebugAdapter(address: URI, proxy: Cancelable)

  private def remote[A: ClassTag](name: String, socket: Socket, service: A)(
      implicit ec: ExecutionContextExecutorService
  ): Future[Remote[A]] = {
    Future(Remote.jsonRPC(name, socket, service))
      .onTimeout(30, TimeUnit.SECONDS)(socket.close())
  }

  def create(
      buildServer: => Option[BuildServerConnection],
      proxyServer: ServerSocket,
      parameters: DebugSessionParams
  )(implicit ec: ExecutionContextExecutorService): Future[DebugAdapterProxy] = {
    val connectToServer = (client: DebugAdapterProxy) => {
      buildServer match {
        case None =>
          Future.failed(new IllegalStateException("No build server"))
        case Some(connection) =>
          connection
            .startDebugSession(parameters)
            .asScala
            .map(uri => new Socket(uri.getHost, uri.getPort))
            .flatMap(remote("DAP-server", _, ServerEndpoint(client)))
      }
    }

    val connectToClient = (server: DebugAdapterProxy) => {
      Future(proxyServer.accept())
        .flatMap(remote("DAP-client", _, ClientEndpoint(server)))
    }

    val proxy = new DebugAdapterProxy(connectToServer, connectToClient)
    proxy
      .connect()
      .map(_ => proxy)
      .onTimeout(3, TimeUnit.SECONDS) {
        proxy.cancel()
      }
  }

  private val gson = new Gson()
  // TODO should return (List[BuildTargetIdentifier], argToForward)
  def parseParameters(arguments: Seq[Any]): Try[b.DebugSessionParams] =
    arguments match {
      case Seq(params: JsonObject) =>
        Try(gson.fromJson(params, classOf[b.DebugSessionParams]))
      case _ =>
        Failure(new IllegalArgumentException(s"arguments: $arguments"))
    }
}
