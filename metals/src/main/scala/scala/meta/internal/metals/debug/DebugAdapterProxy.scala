package scala.meta.internal.metals.debug
import java.net.{ServerSocket, Socket}
import java.util.Collections
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
  Promise,
  TimeoutException
}
import scala.meta.internal.metals.MetalsEnrichments._
import scala.meta.internal.metals.{BuildServerConnection, Cancelable}
import scala.util.{Failure, Success, Try}

class DebugAdapterProxy(sessionFactory: DebugSession.Factory)(
    implicit val ec: ExecutionContextExecutorService
) extends ClientProxy
    with ServerProxy
    with Cancelable {

  private val isCanceled = new AtomicBoolean(false)
  private val isRestarting = new AtomicBoolean(false)
  @volatile private var session: DebugSession = _
  private val terminatedPromise = new AtomicReference(Promise[Unit]())
  private def terminated: Future[Unit] = terminatedPromise.get().future
  private var initializeArgs: InitializeRequestArguments = _

  override def initialize(
      args: InitializeRequestArguments
  ): CompletableFuture[
    Capabilities
  ] = {
    this.initializeArgs = args
    super.initialize(args).thenApply { capabilities =>
      capabilities.setSupportsRestartRequest(true)
      capabilities
    }
  }

  private[DebugAdapterProxy] def connect(): Future[Unit] = {
    if (isCanceled.get()) Future.unit
    else
      sessionFactory.create(this).map { newSession: DebugSession =>
        session = newSession
        session.start()
        if (isCanceled.get()) {
          newSession.server.cancel()
          newSession.client.cancel()
        }
      }
  }

  override def restart(
      args: RestartArguments
  ): CompletableFuture[Void] = {
    if (isRestarting.compareAndSet(false, true)) {
      val args = new DisconnectArguments()
      args.setTerminateDebuggee(true)

      val serverDone = for {
        _ <- super.disconnect(args).asScala
        _ <- terminated
        _ <- Future(session.server.cancel())
      } yield ()

      val bar = for {
        _ <- serverDone
        server <- sessionFactory.openServerConnection(this)
        session = new DebugSession(server, this.session.client)
      } yield {
        this.session = session
        server.start()

        val foo = for {
          _ <- server.service.initialize(initializeArgs).asScala
          _ <- server.service.launch(Collections.emptyMap()).asScala
          _ <- server.service
            .configurationDone(new ConfigurationDoneArguments)
            .asScala
        } yield {}
        foo.onComplete {
          case Failure(exception) =>
            scribe.warn(exception.getMessage)
          case Success(value) =>
        }
      }
      bar.onComplete {
        case Failure(exception) =>
          scribe.warn(exception.getMessage)
          isRestarting.set(false) // TODO handle failure of the above future. otherwise it hangs
        case Success(value) =>
          isRestarting.set(false) // TODO handle failure of the above future. otherwise it hangs
      }

      serverDone.onComplete {
        case Failure(exception) =>
          scribe.warn(exception.getMessage)
        case Success(value) =>
      }
      serverDone.voided
    } else {
      Future.unit.voided // do nothing if already restarting
    }
  }

  override def disconnect(
      args: DisconnectArguments
  ): CompletableFuture[Void] = {
    if (false) { // TODO use args.getRestart after 22.08
//      if (isRestarting.compareAndSet(false, true)) {
//
//        // TODO uncomment after after 22.08 : args.setRestart(false) // since metals is handling the restarting
//        val oldSession = session
//        val terminated =
//          this.terminated.withTimeout(10, TimeUnit.SECONDS)
//
//        terminated
//          .flatMap(_ => {
//            val newSession = for {
//              client <- sessionFactory.awaitClientConnection(this)
//              server <- sessionFactory.openServerConnection(this)
//            } yield new DebugSession(server, client)
//
//            newSession.map { s =>
//              session = s
//              session.start()
//            }
//          })
//          .onComplete {
//            case Failure(exception) =>
//              scribe.warn(exception.getMessage)
//              cancel()
//            case Success(value) =>
//              isRestarting.set(false)
//          }
//
//        super.disconnect(args)
//      } else {
//      }
      Future.unit.voided // do nothing if already restarting
    } else {
      super.disconnect(args)
    }
  }

  override def terminated(args: TerminatedEventArguments): Unit = {
    try {
      if (!isRestarting.get()) {
        super.terminated(args) // TODO comment
      }
    } finally {
      val previousPromise = terminatedPromise.getAndSet(Promise())
      previousPromise.success(())
    }
  }

  def cancel(): Unit = {
    if (isCanceled.compareAndSet(false, true)) {
      val session = this.session
      if (session != null) {
        val args = new DisconnectArguments
        args.setTerminateDebuggee(true)

        val serverDone = for {
          _ <- super.disconnect(args).asScala
          _ <- this.terminated
        } yield ()

        serverDone
          .withTimeout(15, TimeUnit.SECONDS)
          .onComplete(_ => {
            session.server.cancel()
            session.client.cancel()
          })
      }
    }
  }

  override protected[this] def notifyClient(
      f: IDebugProtocolClient => Unit
  ): Unit = {
    if (session != null) {
      f(session.client.service)
    }
  }

  override protected[this] def requestFromServer[A](
      f: IDebugProtocolServer => CompletableFuture[A]
  ): CompletableFuture[A] = {
    if (session != null) {
      f(session.server.service)
    } else {
      Future.failed(new IllegalStateException("No debug session open")).asJava
    }
  }
}

object DebugAdapterProxy {
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
