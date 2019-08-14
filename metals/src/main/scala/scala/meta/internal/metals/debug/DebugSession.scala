package scala.meta.internal.metals.debug

import java.net.{ServerSocket, Socket}
import java.util.concurrent.TimeUnit

import org.eclipse.lsp4j.debug.DisconnectArguments

import scala.concurrent._
import scala.concurrent.duration.FiniteDuration
import scala.meta.internal.metals.MetalsEnrichments._
import scala.meta.internal.metals.{Cancelable, Remote, TryAll}
import scala.reflect.ClassTag

final class DebugSession(
    val server: Remote[ServerEndpoint],
    val client: Remote[ClientEndpoint]
) extends Cancelable {
  val terminated = Promise[Unit]()

  override def cancel(): Unit = {
    def cancelServer(): Unit = {
      val args = new DisconnectArguments
      args.setTerminateDebuggee(true)
      val disconnected = server.service.disconnect(args).asScala

      try {
        val duration = FiniteDuration(15, TimeUnit.SECONDS)
        Await.ready(disconnected, duration)
        Await.ready(terminated.future, duration)
        server.cancel()
      } catch {
        case _: TimeoutException => server.cancel()
      }

    }
    TryAll(
      () => cancelServer(),
      () => client.cancel()
    )
  }
}

object DebugSession {
  final class Factory(
      connectToServer: () => Future[Socket],
      clientFacingServer: ServerSocket
  ) {
    def create(proxy: DebugAdapterProxy)(
        implicit ec: ExecutionContextExecutorService
    ): Future[DebugSession] = {
      for {
        server <- connectToServer()
          .flatMap(remote("DAP-server", _, ServerEndpoint(proxy)))
        client <- Future(clientFacingServer.accept())
          .flatMap(remote("DAP-client", _, ClientEndpoint(proxy)))
      } yield {
        client.start()
        server.start()

        new DebugSession(server, client)
      }
    }

    def remote[A: ClassTag](name: String, socket: Socket, service: A)(
        implicit ec: ExecutionContextExecutorService
    ): Future[Remote[A]] = {
      Future(Remote.jsonRPC(name, socket, service))
        .onTimeout(30, TimeUnit.SECONDS)(socket.close())
    }

  }

}
