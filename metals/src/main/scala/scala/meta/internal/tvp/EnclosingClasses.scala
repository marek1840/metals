package scala.meta.internal.tvp

import org.eclipse.lsp4j.Position
import scala.meta.inputs.Input
import scala.meta.internal.mtags.Mtags
import scala.meta.internal.semanticdb.Scala._
import scala.meta.internal.semanticdb.SymbolOccurrence

object EnclosingClasses {
  def closestClass(
      input: Input.VirtualFile,
      pos: Position
  ): Option[SymbolOccurrence] = {
    val occurrences =
      Mtags.allToplevels(input).occurrences.filterNot(_.symbol.isPackage)
    if (occurrences.isEmpty) None
    else {
      val closestSymbol = occurrences.minBy { occ =>
        val startLine = occ.range.fold(Int.MaxValue)(_.startLine)
        val distance = math.abs(pos.getLine - startLine)
        val isLeading = pos.getLine > startLine
        (!isLeading, distance)
      }
      Some(closestSymbol)
    }
  }
}
