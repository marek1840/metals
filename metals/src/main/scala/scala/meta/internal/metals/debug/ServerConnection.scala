package scala.meta.internal.metals.debug

import java.net.Socket

import org.eclipse.lsp4j.debug.services.{
  IDebugProtocolClient,
  IDebugProtocolServer
}
import org.eclipse.lsp4j.jsonrpc.{JsonRpcException, Launcher}

import scala.concurrent.{
  ExecutionContext,
  ExecutionContextExecutorService,
  Promise
}
import scala.meta.internal.metals.{CancelableFuture, GlobalTrace}

final class ServerConnection(
    val connection: CancelableFuture[Unit],
    val server: IDebugProtocolServer
)(implicit val ec: ExecutionContext)
    extends RemoteConnection
    with ServerProxy

object ServerConnection {
  def open(socket: Socket, service: IDebugProtocolClient)(
      implicit es: ExecutionContextExecutorService
  ): ServerConnection = {
    val launcher = DebugProtocolProxy
      .builder[IDebugProtocolServer](socket, service)
      .traceMessages(GlobalTrace.setup("dap-server"))
      .create()

    val connection = RemoteConnection.start(launcher, socket)
    new ServerConnection(connection, launcher.getRemoteProxy)
  }
}
