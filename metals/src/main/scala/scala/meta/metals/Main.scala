package scala.meta.metals

import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

import org.eclipse.lsp4j.MessageType
import org.eclipse.lsp4j.MessageType.Warning
import org.eclipse.lsp4j.jsonrpc.Launcher

import scala.concurrent.ExecutionContext
import scala.meta.internal.metals.{
  ConfiguredLanguageClient,
  Embedded,
  GlobalTrace,
  JavaDebugInterface,
  MetalsLanguageClient,
  MetalsLanguageServer,
  MetalsServerConfig
}
import scala.util.{Failure, Success}
import scala.util.control.NonFatal

object Main {
  def main(args: Array[String]): Unit = {
    val systemIn = System.in
    val systemOut = System.out
    val tracePrinter = GlobalTrace.setup("LSP")
    val exec = Executors.newCachedThreadPool()
    val ec = ExecutionContext.fromExecutorService(exec)
    val config = MetalsServerConfig.default
    val server = new MetalsLanguageServer(
      ec,
      redirectSystemOut = true,
      charset = StandardCharsets.UTF_8,
      config = config,
      newBloopClassloader = () =>
        Embedded.newBloopClassloader(config.bloopEmbeddedVersion)
    )
    try {
      scribe.info(s"Starting Metals server with configuration: $config")
      val launcher = new Launcher.Builder[MetalsLanguageClient]()
        .traceMessages(tracePrinter)
        .setExecutorService(exec)
        .setInput(systemIn)
        .setOutput(systemOut)
        .setRemoteInterface(classOf[MetalsLanguageClient])
        .setLocalService(server)
        .create()
      val underlyingClient = launcher.getRemoteProxy
      val client = new ConfiguredLanguageClient(underlyingClient, config)(ec)
      server.connectToLanguageClient(client)

      JavaDebugInterface.load match {
        case Success(_) =>
          scribe.debug(s"Java Debug Interface is enabled")
        case Failure(e) =>
          scribe.warn(
            s"Java Debug Interface not available due to: ${e.getMessage}"
          )
      }

      launcher.startListening().get()
    } catch {
      case NonFatal(e) =>
        e.printStackTrace(systemOut)
        sys.exit(1)
    } finally {
      server.cancelAll()
    }
  }

}
