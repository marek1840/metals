package scala.meta.internal.metals.debug

import java.net.URI
import org.eclipse.lsp4j.debug.Source
import scala.meta.internal.metals.ClassPathSourceIndex
import scala.meta.internal.metals.DefinitionProvider
import scala.meta.internal.metals.MetalsEnrichments._
import scala.meta.internal.mtags.OnDemandSymbolIndex
import scala.meta.internal.semanticdb.Scala.Symbols

// TODO what when package does not match the actual directory
//  (e.g package foo.bar in directory baz)?
final class RelativeSourceAdapter(
    classPathSources: ClassPathSourceIndex,
    onDemandSymbolIndex: OnDemandSymbolIndex,
    definitionProvider: DefinitionProvider
) {
  def adapt(source: Source): Boolean = {
    if (source == null) return true

    val classPathLocation = source.getPath
    val actualPath = classPathSources
      .resolve(classPathLocation)
      .map(_.toURI)
      .orElse(findDefinition(source))

    actualPath match {
      case Some(path) =>
        source.setPath(path.toString)
        true
      case None =>
        scribe.debug(s"No sources for $classPathLocation")
        false
    }
  }

  def findDefinition(source: Source): Option[URI] = {
    var symbols = List("", Symbols.RootPackage)
    val segment = new StringBuilder
    val iterator =
      source.getPath
        .stripSuffix(".scala")
        .stripSuffix(".java")
        .iterator

    while (iterator.hasNext) {
      val c = iterator.next()
      if (c == '$') {
        symbols = for {
          qualifier <- symbols
          symbol <- List("#", ".").map(segment + _)
        } yield qualifier + symbol
        segment.clear()
      } else {
        segment.append(c)
      }
    }
    symbols = for {
      qualifier <- symbols
      typeSymbol <- List("#", ".").map(segment + _)
    } yield qualifier + typeSymbol

    val uris = symbols.flatMap { symbol =>
      definitionProvider.fromSymbol(symbol).asScala
    }

    uris.headOption.map(location => URI.create(location.getUri))
  }
}
