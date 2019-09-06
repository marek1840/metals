package scala.meta.internal.metals.debug

import java.net.Socket

import org.eclipse.lsp4j.debug.services.{
  IDebugProtocolClient,
  IDebugProtocolServer
}
import scala.meta.internal.metals.debug.RemoteConnection._
import scala.concurrent.{ExecutionContext, ExecutionContextExecutorService}
import scala.meta.internal.metals.{CancelableFuture, GlobalTrace}

sealed trait ClientConnection extends RemoteConnection with ClientProxy

object ClientConnection {
  private final class Connected(
      val connection: CancelableFuture[Unit],
      val client: IDebugProtocolClient
  )(implicit val ec: ExecutionContext)
      extends ClientConnection

  /**
   * Swallows every notification
   */
  object BlackHole extends ClientConnection {
    override def client: IDebugProtocolClient =
      new IDebugProtocolClient {}

    override def connection: CancelableFuture[Unit] =
      CancelableFuture.successful(())
  }

  def open(socket: Socket, service: IDebugProtocolServer)(
      implicit es: ExecutionContextExecutorService
  ): ClientConnection = {
    val launcher = builder[IDebugProtocolClient](socket, service)
      .traceMessages(GlobalTrace.setup("dap-client"))
      .create()

    val connection = start(launcher, socket)
    new Connected(connection, launcher.getRemoteProxy)
  }
}
