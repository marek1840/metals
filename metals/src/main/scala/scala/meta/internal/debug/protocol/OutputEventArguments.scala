package scala.meta.internal.debug.protocol

import org.eclipse.lsp4j.debug
import org.eclipse.lsp4j.debug.{OutputEventArgumentsCategory => Category}

object OutputEventArguments {
  def stdout(message: String): debug.OutputEventArguments = {
    apply(Category.STDOUT, message)
  }

  def stderr(message: String): debug.OutputEventArguments = {
    apply(Category.STDERR, message)
  }

  private def apply(
      category: String,
      message: String
  ): debug.OutputEventArguments = {
    val output = new debug.OutputEventArguments()
    output.setCategory(category)
    output.setOutput(message)
    output
  }
}
