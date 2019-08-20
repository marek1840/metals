package scala.meta.internal.metals.debug

import java.net.{ServerSocket, Socket}
import java.util.concurrent.TimeUnit

import scala.concurrent._
import scala.meta.internal.metals.MetalsEnrichments._
import scala.reflect.ClassTag

final class DebugSession(
    val server: Remote[ServerEndpoint],
    val client: Remote[ClientEndpoint]
) {
  def start(): Unit = {
    server.start()
    client.start()
  }
}

object DebugSession {
  final class Factory(
      connectToServer: () => Future[Socket],
      clientFacingServer: ServerSocket
  ) {
    def openServerConnection(proxy: DebugAdapterProxy)(
        implicit ec: ExecutionContextExecutorService
    ): Future[Remote[ServerEndpoint]] = {
      connectToServer().flatMap(remote("DAP-server", _, ServerEndpoint(proxy)))
    }

    def awaitClientConnection(proxy: DebugAdapterProxy)(
        implicit ec: ExecutionContextExecutorService
    ): Future[Remote[ClientEndpoint]] = {
      Future(clientFacingServer.accept())
        .flatMap(remote("DAP-client", _, ClientEndpoint(proxy)))
    }

    def create(proxy: DebugAdapterProxy)(
        implicit ec: ExecutionContextExecutorService
    ): Future[DebugSession] = {
      for {
        server <- openServerConnection(proxy)
        client <- awaitClientConnection(proxy)
      } yield {
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
