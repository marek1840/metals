package scala.meta.internal.debug.protocol

import org.eclipse.lsp4j.debug
import org.eclipse.lsp4j.debug.RunInTerminalRequestArgumentsKind._

object RunInTerminalRequestArguments {
  def integrated(
      workingDir: String,
      command: Array[String]
  ): debug.RunInTerminalRequestArguments =
    apply(INTEGRATED, workingDir, command)

  private def apply(
      kind: debug.RunInTerminalRequestArgumentsKind,
      cwd: String,
      command: Array[String]
  ): debug.RunInTerminalRequestArguments = {
    val args = new debug.RunInTerminalRequestArguments()
    args.setKind(kind)
    args.setCwd(cwd)
    args.setArgs(command)
    args
  }
}
