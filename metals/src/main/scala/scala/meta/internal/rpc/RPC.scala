package scala.meta.internal.rpc

import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import org.eclipse.lsp4j.jsonrpc.Launcher
import scala.meta.internal.metals.Cancelable

object RPC {
  final case class IO(input: InputStream, output: OutputStream)
  final case class Connection[T](remote: T, handle: Future[Void])
      extends Cancelable {
    override def cancel(): Unit = {
      handle.cancel(false)
    }
  }

  def connect[T](remoteInterface: Class[T], localClient: AnyRef, io: IO)(
      initialize: Launcher.Builder[T] => Unit
  )(
      implicit executorService: ExecutorService
  ): Connection[T] = {
    val launcher = RPC.launcher(remoteInterface, localClient, io)(initialize)

    val handle = launcher.startListening()

    Connection(launcher.getRemoteProxy, handle)
  }

  def launcher[T](remoteInterface: Class[T], localClient: AnyRef, io: IO)(
      initialize: Launcher.Builder[T] => Unit
  )(implicit executorService: ExecutorService): Launcher[T] = {
    val launcher = new Launcher.Builder[T]
    launcher.setExecutorService(executorService)
    launcher.setRemoteInterface(remoteInterface)
    launcher.setLocalService(localClient)
    launcher.setInput(io.input)
    launcher.setOutput(io.output)
    initialize(launcher)
    launcher.create()
  }
}
