package scala.meta.internal.metals.debug
import org.eclipse.lsp4j.debug._
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient

trait ClientProxy extends IDebugProtocolClient {
  protected[this] def notifyClient(f: IDebugProtocolClient => Unit): Unit

  override def initialized(): Unit =
    notifyClient(_.initialized())

  override def stopped(args: StoppedEventArguments): Unit =
    notifyClient(_.stopped(args))

  override def continued(args: ContinuedEventArguments): Unit =
    notifyClient(_.continued(args))

  override def exited(args: ExitedEventArguments): Unit =
    notifyClient(_.exited(args))

  override def terminated(args: TerminatedEventArguments): Unit =
    notifyClient(_.terminated(args))

  override def thread(args: ThreadEventArguments): Unit =
    notifyClient(_.thread(args))

  override def output(args: OutputEventArguments): Unit =
    notifyClient(_.output(args))

  override def breakpoint(args: BreakpointEventArguments): Unit =
    notifyClient(_.breakpoint(args))

  override def module(args: ModuleEventArguments): Unit =
    notifyClient(_.module(args))

  override def loadedSource(args: LoadedSourceEventArguments): Unit =
    notifyClient(_.loadedSource(args))

  override def process(args: ProcessEventArguments): Unit =
    notifyClient(_.process(args))

  override def capabilities(args: CapabilitiesEventArguments): Unit =
    notifyClient(_.capabilities(args))
}
