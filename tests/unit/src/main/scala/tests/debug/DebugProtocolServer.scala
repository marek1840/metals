package tests.debug

import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption._
import org.eclipse.lsp4j.debug.Capabilities
import org.eclipse.lsp4j.debug.InitializeRequestArguments
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient
import tests.debug.DebugDirectory._
import scala.concurrent.Future
import scala.meta.internal.debug.DebugAdapter
import scala.meta.internal.metals.MetalsEnrichments._
import scala.meta.internal.metals.debug.MetalsDebugAdapter
import scala.meta.internal.metals.debug.protocol.LaunchParameters

protected[debug] final class DebugProtocolServer(
    server: MetalsDebugAdapter
) extends DebugAdapter {

  def initialize(
      arguments: InitializeRequestArguments
  ): Future[Capabilities] = {
    server.initialize(arguments).asScala
  }

  def launch(mainClass: MainClass): Future[Void] = {
    val uri = sourceFile(mainClass.name)
    Files.createDirectories(Paths.get(sourceDir))
    Files.write(Paths.get(uri), mainClass.bytes, CREATE)

    val parameters = LaunchParameters(
      workspace.toString,
      mainClass.name,
      uri.toString
    )

    server.launch(parameters).asScala
  }

  override def setClient(client: IDebugProtocolClient): Unit =
    server.setClient(client)
}
