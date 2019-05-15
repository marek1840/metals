package scala.meta.internal.metals.debug

import java.util.concurrent.CompletableFuture
import ch.epfl.scala.{bsp4j => bsp}
import org.eclipse.lsp4j.debug.Capabilities
import org.eclipse.lsp4j.debug.InitializeRequestArguments
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.meta.internal.debug.DebugAdapter
import scala.meta.internal.debug.JvmDebugAdapter
import scala.meta.internal.metals.BuildTargets
import scala.meta.internal.metals.MetalsEnrichments._
import scala.meta.internal.metals.debug.{protocol => metals}
import scala.meta.io.AbsolutePath

/**
 * Since language client may not know about e.g. classpath, server must fill those missing infos
 */
class MetalsDebugAdapter(
    underlying: JvmDebugAdapter,
    launchArgsAdapter: LaunchArgsAdapter
)(implicit ec: ExecutionContext)
    extends DebugAdapter {

  @JsonRequest
  def initialize(
      args: InitializeRequestArguments
  ): CompletableFuture[Capabilities] =
    underlying.initialize(args)

  @JsonRequest
  def launch(params: metals.LaunchParameters): CompletableFuture[Void] = {
    launchArgsAdapter
      .adapt(params)
      .flatMap(underlying.launch(_).asScala)
      .asJava
  }

  override def setClient(client: IDebugProtocolClient): Unit =
    underlying.setClient(client)
}

object MetalsDebugAdapter {
  def apply(
      compile: Seq[AbsolutePath] => Future[bsp.CompileResult],
      buildTargets: BuildTargets
  )(implicit ec: ExecutionContext): MetalsDebugAdapter = {
    val launchArgsAdapter = new LaunchArgsAdapter(compile, buildTargets)

    new MetalsDebugAdapter(new JvmDebugAdapter(), launchArgsAdapter)
  }
}
