package scala.meta.internal.metals.debug

import org.eclipse.lsp4j.debug.services.{
  IDebugProtocolClient,
  IDebugProtocolServer
}
import org.eclipse.lsp4j.jsonrpc.MessageConsumer
import org.eclipse.lsp4j.jsonrpc.messages.Message

// needs to be a trait, because we create a dynamic proxy for it
trait ClientEndpoint extends IDebugProtocolClient with ServerProxy

object ClientEndpoint {
  def apply(proxy: IDebugProtocolServer): ClientEndpoint =
    new ClientEndpoint {
      override protected val server: IDebugProtocolServer = proxy
    }
}
