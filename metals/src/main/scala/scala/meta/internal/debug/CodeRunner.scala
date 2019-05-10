package scala.meta.internal.debug

import ch.epfl.scala.{bsp4j => b}
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient
import scala.concurrent.Future
import scala.meta.internal.metals.BuildServerConnection
import scala.meta.internal.metals.BuildServerConnectionProvider
import scala.meta.internal.metals.BuildTargets
import scala.meta.internal.metals.MetalsEnrichments._
import scala.meta.internal.metals.MetalsLanguageClient
import scala.meta.io.AbsolutePath

final class CodeRunner(
    buildServer: Option[BuildServerConnection],
    buildTargets: BuildTargets
) {
  def run(file: AbsolutePath): Future[b.RunResult] = {
    val task = for {
      server <- buildServer
      id <- buildTargets.inverseSources(file)
    } yield {
      val params = new b.RunParams(id)
      val task = server.run(params)
      task.asScala
    }

    task.getOrElse {
      val result = new b.RunResult(b.StatusCode.CANCELLED)
      Future.successful(result)
    }
  }
}
