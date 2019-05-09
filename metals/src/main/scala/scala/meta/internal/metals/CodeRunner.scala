package scala.meta.internal.metals

import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import ch.epfl.scala.{bsp4j => b}
import org.eclipse.{lsp4j => l}
import scala.concurrent.Future
import scala.meta.internal.metals.CodeLensCommands.RunCodeArgs
import scala.meta.internal.metals.MetalsEnrichments._
import scala.meta.io.AbsolutePath

final class CodeRunner(
    buildServer: () => Option[BuildServerConnection],
    buildTargets: BuildTargets,
    languageClient: MetalsLanguageClient
) {
  def run(file: AbsolutePath): Future[b.RunResult] = {
    val task = for {
      server <- buildServer()
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
