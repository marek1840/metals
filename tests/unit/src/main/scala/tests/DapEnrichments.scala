package tests
import org.eclipse.lsp4j.debug.Source
import org.eclipse.lsp4j.debug.SourceBreakpoint
import scala.meta.inputs.Position
import scala.meta.io.AbsolutePath
import scala.meta.internal.metals.MetalsEnrichments._

object DapEnrichments {
  implicit class XtensionAbsolutePath(path: AbsolutePath) {
    def toDAP: Source = {
      val source = new Source
      source.setName(path.filename)
      source.setPath(path.toURI.toString)
      source
    }
  }

  implicit class XtensionPosition(position: Position) {
    def toBreakpoint: SourceBreakpoint = {
      val breakpoint = new SourceBreakpoint
      breakpoint.setLine(position.startLine.toLong)
      breakpoint.setColumn(position.startColumn.toLong)
      breakpoint
    }
  }
}
