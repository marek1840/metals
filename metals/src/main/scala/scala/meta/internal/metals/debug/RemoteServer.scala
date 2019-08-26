package scala.meta.internal.metals.debug

import java.util
import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}
import java.util.concurrent.{CompletableFuture, TimeUnit}

import org.eclipse.lsp4j.debug.{Capabilities, InitializeRequestArguments}

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.meta.internal.metals.{Cancelable, Synchronized}
import scala.meta.internal.metals.MetalsEnrichments._

final class RemoteServer(
    connect: () => Future[ServerConnection],
    initialConnection: ServerConnection
)(implicit ec: ExecutionContext)
    extends ServerProxy
    with Cancelable {
  private val isClosed = new AtomicBoolean(false)
  private val connection = new AtomicReference(initialConnection)

  private val config = new AtomicReference[InitializeRequestArguments]()

  override def server: ServerConnection = connection.get()
  private val listeningPromise = Promise[Unit]()

  def listening: Future[Unit] = {
    listeningPromise.future
  }

  private val reconnectError = "Cannot reconnect: remote server is closed"
  def reconnect: Future[Unit] = {
    if (isClosed.get()) {
      Future.failed(new IllegalStateException(reconnectError))
    } else {
      connect()
        .onTimeout(10, TimeUnit.SECONDS)(this.cancel())
        .flatMap(initializeConnection)
    }
  }

  private def initializeConnection(
      newConnection: ServerConnection
  ): Future[Unit] =
    synchronized {
      val oldConnection = connection.getAndSet(newConnection)
      oldConnection.cancel()

      // TODO explain that cancel could occur in the meantime
      if (isClosed.get()) {
        newConnection.cancel()
        Future.failed(new IllegalStateException(reconnectError))
      } else {
        newConnection.listening.onComplete(_ => shutdown(newConnection))
        for {
          _ <- newConnection.initialize(config.get()).asScala
          _ <- newConnection.launch(util.Collections.emptyMap()).asScala
        } yield ()
      }
    }

  private def shutdown(
      newConnection: ServerConnection
  ) = {
    synchronized {
      // without synchronisation there would be a race condition here
      // as another connection could be initialized just after the check
      if (connection.get() == newConnection) {
        this.cancel()
        listeningPromise.success(())
      }
    }
  }

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
