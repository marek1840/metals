package scala.meta.internal.debug.protocol

import org.eclipse.lsp4j.debug

object OutputEventArguments {
  def apply(message: String): debug.OutputEventArguments = {
    val output = new debug.OutputEventArguments()
    output.setOutput(message)
    output
  }
}
