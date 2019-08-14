package scala.meta.internal.metals.debug

import java.util.concurrent.CompletableFuture

import org.eclipse.lsp4j.debug.services.{
  IDebugProtocolClient,
  IDebugProtocolServer
}

// needs to be a trait, because we create a dynamic proxy for it
trait ClientEndpoint extends IDebugProtocolClient with ServerProxy

object ClientEndpoint {
  def apply(proxy: IDebugProtocolServer): ClientEndpoint =
    new ClientEndpoint {
      override protected[this] def requestFromServer[A](
          f: IDebugProtocolServer => CompletableFuture[A]
      ): CompletableFuture[A] = {
        f(proxy)
      }
    }
}
