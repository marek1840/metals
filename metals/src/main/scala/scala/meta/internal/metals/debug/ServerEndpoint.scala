package scala.meta.internal.metals.debug

import org.eclipse.lsp4j.debug.services.{
  IDebugProtocolClient,
  IDebugProtocolServer
}

// needs to be a trait, because we create a dynamic proxy for it
trait ServerEndpoint extends IDebugProtocolServer with ClientProxy

object ServerEndpoint {
  def apply(proxy: IDebugProtocolClient): ServerEndpoint =
    new ServerEndpoint {
      override protected val client: IDebugProtocolClient = proxy
    }
}
