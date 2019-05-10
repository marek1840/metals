package scala.meta.internal.debug

import ch.epfl.scala.{bsp4j => bsp}
import org.eclipse.lsp4j.debug

private[debug] object DebugProtocol {
  object OutputEvent {
    def apply(message: String): debug.OutputEventArguments = {
      val output = new debug.OutputEventArguments()
      output.setOutput(message)
      output
    }

    def apply(params: bsp.LogMessageParams): debug.OutputEventArguments =
      apply(params.getMessage)
  }
}
