package scala.meta.internal.metals.debug

import java.net.Socket

import org.eclipse.lsp4j.jsonrpc.Launcher

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.meta.internal.metals.MetalsEnrichments._
import scala.meta.internal.metals.{Cancelable, CancelableFuture}

trait RemoteConnection extends Cancelable {
  protected def connection: CancelableFuture[Unit]

  final def listening: Future[Unit] = connection.future

  override final def cancel(): Unit = {
    connection.cancel()
  }
}

object RemoteConnection {
  def start(launcher: Launcher[_], socket: Socket)(
      implicit executor: ExecutionContext
  ): CancelableFuture[Unit] = {
    val cancelable = launcher.startListening()
    val future = Future(cancelable.get()).andThen { case _ => socket.close() }
    new CancelableFuture(future.ignoreValue, () => cancelable.cancel(true))
  }
}
