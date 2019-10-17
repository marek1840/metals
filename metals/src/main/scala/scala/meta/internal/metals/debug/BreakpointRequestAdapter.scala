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

  def adapt(
      original: SetBreakpointsArguments
  ): Iterable[SetBreakpointsArguments] = {
    import scala.meta.internal.metals.MetalsEnrichments._

    val uri = adaptPath(original.getSource.getPath)
    val input = uri.toAbsolutePath.toInput
    val occurrences = Mtags.allToplevels(input).occurrences

    val breakpoints = original.getBreakpoints.groupBy { breakpoint =>
      val definition = occurrences.minBy(distanceFrom(breakpoint))
      toFQCN(definition)
    }

    breakpoints.map {
      case (fqcn, breakpoints) =>
        val source = DebugProtocol.copy(original.getSource)
        source.setPath(uri + s"?fqcn=$fqcn")

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
    math.abs(breakpoint.getLine - startLine)
  }

  private def toFQCN(definition: SymbolOccurrence) = {
    import scala.meta.internal.semanticdb.Scala._
    definition.symbol.owner.replaceAll("/", ".") + definition.symbol.desc.name
  }
}
