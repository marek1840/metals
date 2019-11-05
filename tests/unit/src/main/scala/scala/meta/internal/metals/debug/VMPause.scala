package scala.meta.internal.metals.debug
import org.eclipse.lsp4j.debug.Breakpoint

final class VMPause(breakpoint: Breakpoint, hit: BreakpointHit) {
  def sourceLocation: String = {
    breakpoint.getSource.getPath + ":" + breakpoint.getLine
  }
}
