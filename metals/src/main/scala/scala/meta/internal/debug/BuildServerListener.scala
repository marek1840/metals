package scala.meta.internal.debug
import ch.epfl.scala.{bsp4j => bsp}
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient
import scala.meta.internal.bsp.NoopBuildClient

private[debug] final class BuildServerListener(client: IDebugProtocolClient)
    extends NoopBuildClient {
  import DebugProtocol._

  override def onBuildLogMessage(params: bsp.LogMessageParams): Unit = {
    client.output(OutputEvent(params))
    scribe.info("Sent: " + params)
  }
}
