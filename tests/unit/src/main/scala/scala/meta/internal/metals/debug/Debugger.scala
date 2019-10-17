package scala.meta.internal.metals.debug

trait Debugger

object Debugger {
  final case class Running(stoppageHandler: Stoppage.Handler) extends Debugger {}

  final case class Finished(output: String, errorOutput: String)

  final case class Failed(cause: Throwable) extends Debugger
}
