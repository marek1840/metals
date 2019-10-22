package scala.meta.internal.metals.debug
import org.eclipse.lsp4j.debug.Source
import scala.meta.internal.metals.ClassPathSourceIndex
import scala.meta.internal.metals.DefinitionProvider
import scala.meta.internal.mtags.OnDemandSymbolIndex
import scala.meta.internal.semanticdb.Scala.Symbols
import scala.meta.io.AbsolutePath

final class RelativeSourceAdapter(
    classPathSources: ClassPathSourceIndex,
    onDemandSymbolIndex: OnDemandSymbolIndex,
    definitionProvider: DefinitionProvider
) {
  def adapt(source: Source): Unit = {
    if (source == null) return

    val classPathLocation = source.getPath
    classPathSources.resolve(classPathLocation).orElse(foo(source)) match {
      case Some(path) =>
        source.setPath(path.toString())
      case None =>
        scribe.warn("???")
    }
  }

  def resolve(source: Source): Option[AbsolutePath] = {
    import scala.meta.internal.metals.MetalsEnrichments._
    classPathSources.resolve(source.getPath).orElse(foo(source))
  }

  def foo(source: Source): Option[AbsolutePath] = {
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
      symbol <- List("#", ".").map(segment + _)
    } yield qualifier + symbol

    val uris = symbols
      .map(scala.meta.internal.mtags.Symbol.apply)
      .flatMap(onDemandSymbolIndex.definition)
      .map(_.path)
    uris.headOption
  }
}
