package scala.meta.internal.debug
import java.net.ServerSocket
import java.net.Socket
import java.util
import java.util.concurrent.CompletableFuture
import org.eclipse.lsp4j.debug
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient
import org.eclipse.lsp4j.jsonrpc.debug.DebugLauncher
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.meta.internal.metals.CodeRunner
import scala.meta.internal.metals.MetalsEnrichments._
import scala.meta.metals.Main

// Note: Inheriting from IDebugProtocolServer causes duplicate methods as the rpc
// scans an interface first and then fails for respective methods in the implementation
final class ScalaDebugAdapter(codeRunner: CodeRunner)(
    implicit val ex: ExecutionContext
) {

  @JsonRequest
  def initialize(
      args: debug.InitializeRequestArguments
  ): CompletableFuture[
    debug.Capabilities
  ] = {
    scribe.info("Initializing scala debug adapter")

    Future {
      new debug.Capabilities()
    }.asJava
  }

  @JsonRequest
  def launch(
      args: util.Map[String, AnyRef]
  ): CompletableFuture[Void] = {
    scribe.info("Launching: " + args)
    val path = args.get("file").asInstanceOf[String].toAbsolutePath

    val task = for {
      _ <- codeRunner.run(path)
      void <- new CompletableFuture[Void]().asScala
    } yield void

    task.asJava
  }
}

object ScalaDebugAdapter {
  private def createSocket(): ServerSocket = {
    val serverSocket = new ServerSocket(0)
    Runtime.getRuntime.addShutdownHook(new Thread(() => {
      serverSocket.close()
    }))
    serverSocket
  }

  private def attach(
      adapter: ScalaDebugAdapter,
      client: Socket
  )(implicit ex: ExecutionContext): util.concurrent.Future[Void] = {
    val launcher = new DebugLauncher.Builder[IDebugProtocolClient]()
      .setLocalService(adapter)
      .setInput(client.getInputStream)
      .setOutput(client.getOutputStream)
      .setRemoteInterface(classOf[IDebugProtocolClient])
      .setExecutorService(Main.exec)
      .create()

    launcher.startListening()
  }

  def create(
      codeRunner: CodeRunner
  )(implicit ex: ExecutionContext): java.lang.Integer = {
    val adapter = new ScalaDebugAdapter(codeRunner)
    val server = createSocket()

    for {
      client <- Future { server.accept() }
      _ = attach(adapter, client).get()
    } scribe.info(
      s"Terminated debug adapter listening at ${server.getLocalSocketAddress}"
    )

    server.getLocalPort
  }
}
