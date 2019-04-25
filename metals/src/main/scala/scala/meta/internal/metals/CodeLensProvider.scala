package scala.meta.internal.metals

import java.util
import org.eclipse.lsp4j.CodeLens
import org.eclipse.{lsp4j => l}
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.meta.internal.metals.MetalsEnrichments._
import scala.meta.internal.mtags.Semanticdbs
import scala.meta.io.AbsolutePath

trait CodeLensProvider {
  def findLenses(path: AbsolutePath): Future[util.List[l.CodeLens]]

  protected implicit final class XtensionCommand(command: Command[_]) {
    def lens(range: l.Range, arguments: List[AnyRef]): l.CodeLens = {
      val lspCommand = command.toLSP(arguments)
      new l.CodeLens(range, lspCommand, null)
    }

    def toLSP(arguments: List[AnyRef]): l.Command =
      new l.Command(command.title, command.id, arguments.asJava)
  }
}

object CodeLensProvider {
  def create(
      buildTargets: BuildTargets,
      buildServer: BuildServerConnection,
      semanticdbs: Semanticdbs,
      buffers: Buffers
  )(implicit ec: ExecutionContext): CodeLensProvider = {
    new AggregatedProvider(
      new RunCodeLensProvider(buildTargets, buildServer, semanticdbs, buffers),
      new TestLensProvider(buildTargets, buildServer, semanticdbs, buffers)
    )
  }

  private class AggregatedProvider(providers: CodeLensProvider*)(
      implicit ec: ExecutionContext
  ) extends CodeLensProvider() {
    override def findLenses(path: AbsolutePath): Future[util.List[CodeLens]] =
      for {
        lenses <- Future.sequence(providers.map(_.findLenses(path)))
      } yield lenses.flatMap(_.asScala).asJava
  }
}
