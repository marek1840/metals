package scala.meta.internal.metals.debug

import scala.meta.internal.metals.debug.{protocol => metals}
import java.util.concurrent.CompletableFuture
import org.eclipse.lsp4j.debug.Capabilities
import org.eclipse.lsp4j.debug.InitializeRequestArguments
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest
import scala.concurrent.ExecutionContext
import scala.meta.internal.debug.DebugAdapter
import scala.meta.internal.debug.ScalaDebugAdapter
import scala.meta.internal.metals.BuildTargets

/**
 * Since language client may not know about e.g. classpath, server must fill those missing infos
 */
class MetalsDebugAdapter(
    underlying: ScalaDebugAdapter,
    launchArgsAdapter: LaunchArgsAdapter
) extends DebugAdapter {

  @JsonRequest
  def initialize(
      args: InitializeRequestArguments
  ): CompletableFuture[Capabilities] =
    underlying.initialize(args)

  @JsonRequest
  def launch(params: metals.LaunchParameters): CompletableFuture[Void] = {
    launchArgsAdapter.adapt(params) match {
      case None => CompletableFuture.completedFuture(null)
      case Some(parameters) => underlying.launch(parameters)
    }
  }

  override def setClient(client: IDebugProtocolClient): Unit =
    underlying.setClient(client)
}

object MetalsDebugAdapter {
  def apply(
      buildTargets: BuildTargets
  )(implicit ec: ExecutionContext): MetalsDebugAdapter = {
    val launchArgsAdapter = new LaunchArgsAdapter(buildTargets)

    new MetalsDebugAdapter(new ScalaDebugAdapter, launchArgsAdapter)
  }
}
