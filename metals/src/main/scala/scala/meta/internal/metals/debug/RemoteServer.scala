package scala.meta.internal.metals.debug

import java.util
import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}
import java.util.concurrent.{CompletableFuture, TimeUnit}

import org.eclipse.lsp4j.debug.{
  Capabilities,
  ConfigurationDoneArguments,
  DisconnectArguments,
  InitializeRequestArguments
}

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.meta.internal.metals.Cancelable
import scala.meta.internal.metals.MetalsEnrichments._
import scala.util.control.NonFatal

/**
 * Maintains a connection with the remote debug adapter.
 * Makes restarting possible via reconnecting with the
 * completely new debug session
 */
final class RemoteServer(
    connect: () => Future[ServerConnection],
    firstConnection: ServerConnection
)(implicit ec: ExecutionContext)
    extends ServerProxy
    with Cancelable {
  private val isClosed = new AtomicBoolean(false)
  private val listeningPromise = Promise[Unit]()

  // synchronized to avoid race condition with [[attemptShutdown]]
  private val connection = synchronized {
    firstConnection.listening.onComplete(_ => attemptShutdown(firstConnection))
    new AtomicReference(firstConnection)
  }

  /**
   * Part of the configuration sent to the new debug adapter when reconnecting
   */
  private val config = new AtomicReference[InitializeRequestArguments]()

  override def server: ServerConnection = connection.get()

  /**
   * Completed once the underlying [[connection]] completes listening (@see [[attemptShutdown]])
   */
  def listening: Future[Unit] = {
    listeningPromise.future
  }

  private val reconnectError = new IllegalStateException(
    "Cannot reconnect: remote server is closed"
  )

  def reconnect: Future[Unit] = {
    if (isClosed.get()) {
      Future.failed(reconnectError)
    } else {
      connect()
        .onTimeout(10, TimeUnit.SECONDS)(this.cancel())
        .flatMap(initializeConnection)
    }
  }

  /**
   * Synchronized to avoid a race condition with [[attemptShutdown]]
   */
  private def initializeConnection(
      newConnection: ServerConnection
  ): Future[Unit] = synchronized {
    val oldConnection = connection.getAndSet(newConnection)
    val discarded = discard(oldConnection).recover { case NonFatal(_) => () }

    // server can get closed before the new connection gets acquired
    if (isClosed.get()) {
      newConnection.cancel()
      discarded.flatMap(_ => Future.failed(reconnectError))
    } else {
      newConnection.listening.onComplete(_ => attemptShutdown(newConnection))

      for {
        _ <- newConnection.initialize(config.get()).asScala
        _ <- newConnection.launch.asScala
        _ <- newConnection.configurationDone.asScala
      } yield ()
    }
  }

  /**
   * Tries to terminate gracefully within time limit and then closes the connection
   */
  private def discard(connection: ServerConnection): Future[Unit] = {
    val arguments = new DisconnectArguments
    arguments.setTerminateDebuggee(true)
    val disconnected = connection.disconnect(arguments).asScala.ignoreValue

    disconnected
      .withTimeout(5, TimeUnit.SECONDS)
      .andThen { case _ => connection.cancel() }
  }

  /**
   * Closes the server if the terminated connection is the current connection.
   * It isn't when the server was reconnected (@see [[initializeConnection]])
   */
  private def attemptShutdown(terminated: ServerConnection): Unit = {
    synchronized {
      // without synchronisation there would be a race condition here
      // as another connection could be initialized just after the check
      if (connection.get() == terminated) {
        this.cancel()
        listeningPromise.success(())
      }
    }
  }

  /**
   * Captures the configuration used to initialize the remote debug adapter.
   * It will be used to initialize subsequent connections
   *
   * @see [[config]]
   */
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
