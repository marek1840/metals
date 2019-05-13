package scala.meta.internal.debug.protocol

import org.eclipse.lsp4j.debug

object ExitedEventArguments {
  def apply(exitCode: Int): debug.ExitedEventArguments = {
    val arguments = new debug.ExitedEventArguments
    arguments.setExitCode(exitCode.toLong)
    arguments
  }
}
