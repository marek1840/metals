package scala.meta.internal.metals.debug
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.util.concurrent.TimeUnit
import org.eclipse.lsp4j.debug.Capabilities
import org.eclipse.lsp4j.debug.OutputEventArguments
import org.eclipse.lsp4j.debug.SetBreakpointsResponse
import org.eclipse.lsp4j.debug.StoppedEventArguments
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.meta.inputs.Position
import scala.meta.internal.metals.MetalsEnrichments._
import scala.meta.io.AbsolutePath
import tests.DapEnrichments._
import scala.util.Failure
import scala.util.Success

final class DebugAdapterClient(
    connect: RemoteServer.Listener => Debugger,
    onStoppage: Stoppage.Handler
)(implicit ec: ExecutionContext)
    extends RemoteServer.Listener {
  @volatile private var debugger = connect(this)
  @volatile private var terminated: Promise[Unit] = Promise()
  @volatile private var output = new DebuggeeOutput
  @volatile private var breakpoints = new DebuggeeBreakpoints()
  @volatile private var failed: Option[Throwable] = None

  def initialize: Future[Capabilities] = {
    ifNotFailed(debugger.initialize)
  }

  def launch: Future[Unit] = {
    ifNotFailed(debugger.launch)
  }

  def configurationDone: Future[Unit] = {
    ifNotFailed(debugger.configurationDone)
  }

  def setBreakpoints(
      path: AbsolutePath,
      position: Position
  ): Future[SetBreakpointsResponse] = {
    val source = path.toDAP
    val breakpoints = Array(position.toBreakpoint)
    ifNotFailed(debugger.setBreakpoints(source, breakpoints))
      .map { response =>
        // the breakpoint notification we receive does not contain the source
        // hence we have to register breakpoints here
        response.getBreakpoints.foreach(this.breakpoints.register)
        response
      }
  }

  def restart: Future[Unit] = {
    ifNotFailed(debugger.restart)
      .andThen {
        case _ =>
          debugger = connect(this)
          terminated = Promise()
          output = new DebuggeeOutput
          breakpoints = new DebuggeeBreakpoints
      }
  }

  def disconnect: Future[Unit] = {
    ifNotFailed(debugger.disconnect)
  }

  /**
   * Not waiting for exited because it might not be sent
   */
  def shutdown: Future[Unit] = {
    for {
      _ <- terminated.future
      _ <- debugger.shutdown
    } yield ()
  }

  def awaitOutput(prefix: String, seconds: Int = 5): Future[Unit] = {
    import org.eclipse.lsp4j.debug.{OutputEventArgumentsCategory => Category}
    ifNotFailed(output.awaitPrefix(Category.STDOUT, prefix))
      .withTimeout(seconds, TimeUnit.SECONDS)
  }

  def allOutput: Future[String] = {
    import org.eclipse.lsp4j.debug.{OutputEventArgumentsCategory => Category}
    terminated.future.map { _ =>
      output.get(Category.STDOUT)
    }
  }

  override def onStopped(event: StoppedEventArguments): Unit = {
    val nextStep = for {
      frame <- ifNotFailed(debugger.stackFrame(event.getThreadId))
      cause <- {
        import org.eclipse.lsp4j.debug.{StoppedEventArgumentsReason => Reason}
        event.getReason match {
          case Reason.BREAKPOINT =>
            breakpoints.byStackFrame(frame) match {
              case Some(breakpoint) =>
                Future.successful(Stoppage.Cause.Breakpoint(breakpoint))
              case None =>
                val error = s"No breakpoint for ${frame.info.getSource}"
                Future.failed(new IllegalStateException(error))
            }
          case Reason.STEP =>
            Future.successful(Stoppage.Cause.Step)
          case reason =>
            val error = s"Unsupported stoppage reason: $reason"
            Future.failed(new IllegalStateException(error))
        }
      }
      nextStep <- onStoppage(Stoppage(frame, cause))
    } yield nextStep

    nextStep.onComplete {
      case Failure(error) =>
        fail(error)
      case Success(step) =>
        debugger.step(event.getThreadId, step).recover {
          case error => fail(error)
        }
    }
  }

  override def onOutput(event: OutputEventArguments): Unit = {
    output.append(event)
  }

  override def onTerminated(): Unit = {
    terminated.success(())
  }

  private def fail(error: Throwable): Unit = {
    failed = Some(error)
    terminated.tryFailure(error)
    disconnect.andThen { case _ => debugger.shutdown }
  }

  private def ifNotFailed[A](action: => Future[A]): Future[A] = {
    failed match {
      case None =>
        action
      case Some(error) =>
        Future.failed(error)
    }
  }
}

object DebugAdapterClient {
  private val timeout = TimeUnit.SECONDS.toMillis(60).toInt

  def apply(uri: URI, stoppageHandler: Stoppage.Handler)(
      implicit ec: ExecutionContext
  ): DebugAdapterClient = {
    def connect(listener: RemoteServer.Listener): Debugger = {
      val socket = new Socket()
      socket.connect(new InetSocketAddress(uri.getHost, uri.getPort), timeout)
      val server = RemoteServer(socket, listener)
      new Debugger(server)
    }

    new DebugAdapterClient(connect, stoppageHandler)
  }

}
