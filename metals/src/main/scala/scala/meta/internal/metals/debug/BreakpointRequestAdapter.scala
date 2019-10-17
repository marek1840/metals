package scala.meta.internal.metals.debug
import org.eclipse.lsp4j.debug.SetBreakpointsArguments
import org.eclipse.lsp4j.debug.SourceBreakpoint
import scala.meta.internal.mtags.Mtags
import scala.meta.internal.semanticdb.SymbolOccurrence

private[debug] final class BreakpointRequestAdapter {
  private var adaptPath: String => String = identity

  def adaptPathToURI(): Unit = {
    adaptPath = path => "file://" + path
  }

  def partition(
      original: SetBreakpointsArguments
  ): Iterable[SetBreakpointsArguments] = {
    import scala.meta.internal.metals.MetalsEnrichments._

    val uri = adaptPath(original.getSource.getPath)
    val input = uri.toAbsolutePath.toInput

    val occurrences = Mtags.allToplevels(input).occurrences
    val groups = original.getBreakpoints.groupBy { breakpoint =>
      val definition = occurrences.minBy(distanceFrom(breakpoint))
      toFQCN(definition)
    }

    groups.map {
      case (fqcn, breakpoints) =>
        val source = DebugProtocol.copy(original.getSource)
//        source.setName(fqcn)
        source.setPath(s"dap-fqcn:$fqcn")

        val lines = breakpoints.map(_.getLine).distinct

        val request = new SetBreakpointsArguments
        request.setBreakpoints(breakpoints)
        request.setSource(source)
        request.setLines(lines)
        request.setSourceModified(original.getSourceModified)

        request
    }
  }
  private def distanceFrom(
      breakpoint: SourceBreakpoint
  ): SymbolOccurrence => Long = { occ =>
    val startLine = occ.range.fold(Int.MaxValue)(_.startLine)
    // TODO - 1 because of vscode config
    math.abs(breakpoint.getLine - 1 - startLine)
  }

  private def toFQCN(definition: SymbolOccurrence) = {
    import scala.meta.internal.semanticdb.Scala._
    def qualifier(symbol: String, acc: List[String]): String = {
      val owner = symbol.owner
      if (owner == Symbols.RootPackage || owner == Symbols.EmptyPackage) {
        acc.mkString
      } else {
        val desc = owner.desc
        // assumption: can only be a package or type
        val delimiter = if (desc.isPackage) "." else "$"
        val segment = desc.name + delimiter
        qualifier(owner, segment :: acc)
      }
    }

    def name(symbol: String): String = {
      val desc = symbol.desc
      val name = desc.name

      val suffix = if (desc.isTerm) "$" else ""
      name + suffix
    }

    val symbol = definition.symbol
    val fqcn = qualifier(symbol, Nil) + name(symbol)
    fqcn
  }
}
