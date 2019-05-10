package scala.meta.internal.debug
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient
import scala.meta.internal.bsp.NoopBuildClient
import ch.epfl.scala.{bsp4j => bsp}
import scala.concurrent.Future
import scala.meta.internal.metals.BuildServerConnection
import scala.meta.internal.metals.BuildServerConnectionProvider

object BuildServer {
  final class Listener(client: IDebugProtocolClient) extends NoopBuildClient {
    import DebugProtocol._

    override def onBuildLogMessage(params: bsp.LogMessageParams): Unit = {
      client.output(OutputEvent(params))
      scribe.info("Sent: " + params)
    }
  }

  final class ConnectionProvider(
      client: IDebugProtocolClient,
      provider: BuildServerConnectionProvider
  ) {

    def openConnection(): Future[Option[BuildServerConnection]] =
      provider.openConnection(client)
  }
}
