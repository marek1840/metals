package scala.meta.internal.metals.debug
import java.net.{ServerSocket, Socket}
import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}
import java.util.concurrent.{CompletableFuture, TimeUnit}

import ch.epfl.scala.bsp4j.DebugSessionParams
import ch.epfl.scala.{bsp4j => b}
import com.google.gson.{Gson, JsonObject}
import org.eclipse.lsp4j.debug._
import org.eclipse.lsp4j.debug.services.{
  IDebugProtocolClient,
  IDebugProtocolServer
}

import scala.concurrent.{
  ExecutionContextExecutorService,
  Future,
  TimeoutException
}
import scala.meta.internal.metals.MetalsEnrichments._
import scala.meta.internal.metals.{BuildServerConnection, Cancelable}
import scala.util.{Failure, Try}

class DebugAdapterProxy(sessionFactory: DebugSession.Factory)(
    implicit val ec: ExecutionContextExecutorService
) extends ClientProxy
    with ServerProxy
    with Cancelable {

  private val isCanceled = new AtomicBoolean(false)
  private val isRestarting = new AtomicBoolean(false)
  @volatile private val session = new AtomicReference[DebugSession]()

//  @volatile var state: State = Idle

  private[DebugAdapterProxy] def connect(): Future[Unit] = {
    if (isCanceled.get()) Future.unit
    else {
      sessionFactory.create(this).map { newSession =>
        val oldSession = this.session.getAndSet(newSession)

        if (oldSession != null) {
          oldSession.cancel()
        }

        if (isCanceled.get()) {
          newSession.cancel()
        }
      }
    }
  }

  override def disconnect(
      args: DisconnectArguments
  ): CompletableFuture[Void] = {
    if (args.getRestart) {
      if (isRestarting.compareAndSet(false, true)) {
        args.setRestart(false) // since metals is handling the restarting

        val serverSessionTerminated = {
          val terminated = this.session.get().terminated.future
          terminated
            .withTimeout(10, TimeUnit.SECONDS)
            .recover { case _: TimeoutException => () }
        }

        //          super
//            .disconnect(args)
//            .asScala
//            .flatMap(_ => this.session.get().terminated.future)
//            .withTimeout(10, TimeUnit.SECONDS)
//            .recover { case _: TimeoutException => () }

        // accept new connection in the background
        connect().onComplete(_ => {
          isRestarting.set(false)
        })

        serverSessionTerminated.voided
      } else {
        Future.unit.voided // do nothing if already restarting
      }
    } else {
      super.disconnect(args)
    }
  }

  override def terminated(args: TerminatedEventArguments): Unit = {
    if (isRestarting.get()) {
      args.setRestart(true)
    }

    try super.terminated(args)
    finally session.get().terminated.success(())
  }

  def cancel(): Unit = {
    if (isCanceled.compareAndSet(false, true)) {
      val session = this.session.get()
      if (session != null) {
        session.cancel()
      }
    }
  }

  override protected[this] def notifyClient(
      f: IDebugProtocolClient => Unit
  ): Unit = {
    val session = this.session.get()
    if (session != null) {
      f(session)
    }
  }

  override protected[this] def requestFromServer[A](
      f: IDebugProtocolServer => CompletableFuture[A]
  ): CompletableFuture[A] = {
    val session = this.session.get()
    if (session != null) {
      f(session)
    } else {
      Future.failed(new IllegalStateException("No debug session open")).asJava
    }
  }

}

object DebugAdapterProxy {
  sealed trait State
  final case object Idle extends State
  final case object Canceled extends State
  final case class Running(session: DebugSession) extends State
  final case class Restarting(previousSessionTerminated: Future[Unit])
      extends State

  def create(
      buildServer: => Option[BuildServerConnection],
      proxyServer: ServerSocket,
      parameters: DebugSessionParams
  )(implicit ec: ExecutionContextExecutorService): Future[DebugAdapterProxy] = {
    val connectToServer = () => {
      buildServer match {
        case None =>
          Future.failed(new IllegalStateException("No build server"))
        case Some(connection) =>
          connection
            .startDebugSession(parameters)
            .asScala
            .map(uri => new Socket(uri.getHost, uri.getPort))
      }
    }

    val sessionFactory = new DebugSession.Factory(connectToServer, proxyServer)

    val proxy = new DebugAdapterProxy(sessionFactory)
    proxy
      .connect()
      .map(_ => proxy)
      .onTimeout(10, TimeUnit.SECONDS)(proxy.cancel())
  }

  private val gson = new Gson()
  def parseParameters(arguments: Seq[Any]): Try[b.DebugSessionParams] =
    arguments match {
      case Seq(params: JsonObject) =>
        Try(gson.fromJson(params, classOf[b.DebugSessionParams]))
      case _ =>
        Failure(new IllegalArgumentException(s"arguments: $arguments"))
    }
}
