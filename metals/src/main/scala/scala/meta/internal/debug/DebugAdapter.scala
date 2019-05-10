package scala.meta.internal.debug
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient

trait DebugAdapter {
  def setClient(client: IDebugProtocolClient): Unit
}
