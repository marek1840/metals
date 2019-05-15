package scala.meta.internal.debug
import java.net.ServerSocket
import java.net.Socket
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient
import org.eclipse.lsp4j.jsonrpc.Launcher
import org.eclipse.lsp4j.jsonrpc.debug.DebugLauncher
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.meta.metals.Main

object JvmDebugServer {
  def launch(
      adapter: DebugAdapter
  )(implicit ec: ExecutionContext): java.lang.Integer = {
    val server = createSocket()
    startListening(server, adapter)
    server.getLocalPort
  }

  private def createSocket(): ServerSocket = {
    val serverSocket = new ServerSocket(0)
    Runtime.getRuntime.addShutdownHook(new Thread(() => {
      serverSocket.close()
    }))
    serverSocket
  }

  private def startListening(server: ServerSocket, adapter: DebugAdapter)(
      implicit ec: ExecutionContext
  ): Unit = {
    for {
      client <- Future { server.accept() }
    } launch(client, adapter)
  }

  private def launch(clientSocket: Socket, adapter: DebugAdapter)(
      implicit ec: ExecutionContext
  ): Future[Unit] = {
    val launcher = createLauncher(adapter, clientSocket)
    adapter.setClient(launcher.getRemoteProxy)

    Future { launcher.startListening().get() }.map(_ => ())
  }

  private def createLauncher(
      adapter: AnyRef,
      clientSocket: Socket
  ): Launcher[IDebugProtocolClient] =
    new DebugLauncher.Builder[IDebugProtocolClient]()
      .setLocalService(adapter)
      .setInput(clientSocket.getInputStream)
      .setOutput(clientSocket.getOutputStream)
      .setRemoteInterface(classOf[IDebugProtocolClient])
      .setExecutorService(Main.exec)
      .create()
}
