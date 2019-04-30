package scala.meta.internal.metals
import java.util
import java.util.Collections._
import org.eclipse.{lsp4j => l}
import scala.meta.internal.metals.MetalsEnrichments._
import scala.meta.internal.metals.TokenEditDistance.fromBuffer
import scala.meta.internal.mtags.Semanticdbs
import scala.meta.io.AbsolutePath

final class RunCodeLensProvider(
    cache: PostCompileCache,
    buffers: Buffers,
    semanticdbs: Semanticdbs
) {

  def findLenses(path: AbsolutePath): util.List[l.CodeLens] = {
    scribe.info("Requested code lenses")
    if (cache.mainClasses.isEmpty) emptyList[l.CodeLens]()
    else findLocations(path).asJava
  }

  private def findLocations(
      path: AbsolutePath
  ): List[l.CodeLens] = {
    semanticdbs.textDocument(path).getE match {
      case Right(textDocument) =>
        val distance = fromBuffer(path, textDocument.text, buffers)

        val lenses = for {
          occurrence <- textDocument.occurrences
          if cache.mainClasses.containsKey(occurrence.symbol)
          mainClass = cache.mainClasses.get(occurrence.symbol)
          range <- occurrence.range
            .map(_.toLSP)
            .flatMap(distance.toRevised)
            .toSeq
          arguments = List(
            path.toURI.toString,
            mainClass.getClassName
          )
        } yield
          new l.CodeLens(range, CodeLensCommands.RunCode.toLSP(arguments), null)
        lenses.toList
      case _ => Nil
    }
  }
}
