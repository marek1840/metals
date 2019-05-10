package scala.meta.internal.debug
import java.net.ServerSocket
import java.net.Socket
import java.util
import java.util.concurrent.CompletableFuture
import ch.epfl.scala.{bsp4j => bsp}
import org.eclipse.lsp4j.debug
import org.eclipse.lsp4j.debug.TerminatedEventArguments
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient
import org.eclipse.lsp4j.jsonrpc.Launcher
import org.eclipse.lsp4j.jsonrpc.debug.DebugLauncher
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.meta.internal.metals.BuildServerConnectionProvider
import scala.meta.internal.metals.BuildTargets
import scala.meta.internal.metals.MetalsEnrichments._
import scala.meta.metals.Main

// Note: Inheriting from IDebugProtocolServer causes duplicate methods as the rpc
// scans an interface first and then fails for respective methods in the implementation
final class ScalaDebugAdapter(
    codeRunner: CodeRunner,
    client: IDebugProtocolClient
)(
    implicit val ex: ExecutionContext
) {

  private implicit def statusCodeToExitArgs(
      statusCode: bsp.StatusCode
  ): debug.ExitedEventArguments = {
    val args = new debug.ExitedEventArguments
    args.setExitCode(statusCode.getValue.toLong)
    args
  }

  @JsonRequest
  def initialize(
      args: debug.InitializeRequestArguments
  ): CompletableFuture[
    debug.Capabilities
  ] = {
    scribe.info("Initializing scala debug adapter")

    val capabilities = new debug.Capabilities()
    CompletableFuture.completedFuture(capabilities)
  }

  @JsonRequest
  def launch(
      args: util.Map[String, AnyRef]
  ): CompletableFuture[Void] = {
    scribe.info("Launching: " + args)
    val path = args.get("file").asInstanceOf[String].toAbsolutePath

    for {
      result <- codeRunner.run(path)
    } {
      client.terminated(new TerminatedEventArguments)
      client.exited(result.getStatusCode)
    }

    CompletableFuture.completedFuture(null)
  }
}

object ScalaDebugAdapter {
  def create(
      buildTargets: BuildTargets,
      connectionProvider: BuildServerConnectionProvider
  )(implicit ex: ExecutionContext): java.lang.Integer = {
    val server = createSocket()
    val factory = new AdapterFactory(buildTargets, connectionProvider)

    for {
      client <- Future { server.accept() }
      _ <- attach(factory, client)
    } scribe.info(
      s"Terminated debug adapter listening at ${server.getLocalSocketAddress}"
    )

    server.getLocalPort
  }

  private def createSocket(): ServerSocket = {
    val serverSocket = new ServerSocket(0)
    Runtime.getRuntime.addShutdownHook(new Thread(() => {
      serverSocket.close()
    }))
    serverSocket
  }

  private def attach(
      adapterFactory: AdapterFactory,
      clientSocket: Socket
  )(implicit ex: ExecutionContext): Future[Unit] = {
    val client = new DelegatingDebugClient

    for {
      adapter <- adapterFactory.create(client)
      launcher = createLauncher(adapter, clientSocket)
      _ <- {
        client.underlying = launcher.getRemoteProxy
        Future { launcher.startListening().get() }
      }
    } yield ()
  }

  private def createLauncher(
      adapter: ScalaDebugAdapter,
      clientSocket: Socket
  ): Launcher[IDebugProtocolClient] =
    new DebugLauncher.Builder[IDebugProtocolClient]()
      .setLocalService(adapter)
      .setInput(clientSocket.getInputStream)
      .setOutput(clientSocket.getOutputStream)
      .setRemoteInterface(classOf[IDebugProtocolClient])
      .setExecutorService(Main.exec)
      .create()

  final class AdapterFactory(
      buildTargets: BuildTargets,
      connectionProvider: BuildServerConnectionProvider
  )(implicit val ec: ExecutionContext) {
    def create(client: IDebugProtocolClient): Future[ScalaDebugAdapter] = {
      val listener = new BuildServerListener(client)
      for {
        connection <- connectionProvider.openConnection(listener)
        if connection.isDefined
        codeRunner = new CodeRunner(connection, buildTargets)
      } yield new ScalaDebugAdapter(codeRunner, client)
    }
  }
}
