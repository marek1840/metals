package scala.meta.internal.metals.debug

import org.eclipse.lsp4j.debug.Source
import scala.meta.internal.metals.ClassPathSourceIndex
import scala.meta.internal.metals.DefinitionProvider
import scala.meta.internal.metals.MetalsEnrichments._
import scala.meta.internal.mtags.OnDemandSymbolIndex
import scala.meta.internal.semanticdb.Scala.Symbols

final class RelativeSourceAdapter(
    classPathSources: ClassPathSourceIndex,
    onDemandSymbolIndex: OnDemandSymbolIndex,
    definitionProvider: DefinitionProvider
) {
  def adapt(source: Source): Boolean = {
    if (source == null) return true

    val classPathLocation = source.getPath
    classPathSources.resolve(classPathLocation).orElse(foo(source)) match {
      case Some(path) =>
        source.setPath(path.toString)
        true
      case None =>
        scribe.debug(s"No sources for $classPathLocation")
        false
    }
  }

  def foo(source: Source): Option[String] = {
    var symbols = List("", Symbols.RootPackage)
    val segment = new StringBuilder
    val iterator =
      source.getPath.stripSuffix(".scala").stripSuffix(".java").iterator
    while (iterator.hasNext) {
      iterator.next() match {
        case '$' =>
          symbols = for {
            qualifier <- symbols
            symbol <- List("#", ".").map(segment + _)
          } yield qualifier + symbol
          segment.clear()
        case c =>
          segment.append(c)
      }
    }
    symbols = for {
      qualifier <- symbols
      typeSymbol <- List("#", ".").map(segment + _)
    } yield qualifier + typeSymbol

    val uris = symbols.flatMap { symbol =>
      definitionProvider.fromSymbol(symbol).asScala.map(_.getUri)
    }

    uris.headOption
  }
}
