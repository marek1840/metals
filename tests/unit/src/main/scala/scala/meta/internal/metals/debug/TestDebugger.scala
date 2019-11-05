package scala.meta.internal.metals.debug

import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.util.Collections
import java.util.concurrent.TimeUnit
import org.eclipse.lsp4j.debug.Breakpoint
import org.eclipse.lsp4j.debug.BreakpointEventArguments
import org.eclipse.lsp4j.debug.Capabilities
import org.eclipse.lsp4j.debug.ConfigurationDoneArguments
import org.eclipse.lsp4j.debug.ContinueArguments
import org.eclipse.lsp4j.debug.ContinueResponse
import org.eclipse.lsp4j.debug.DisconnectArguments
import org.eclipse.lsp4j.debug.InitializeRequestArguments
import org.eclipse.lsp4j.debug.OutputEventArguments
import org.eclipse.lsp4j.debug.ScopesArguments
import org.eclipse.lsp4j.debug.ScopesResponse
import org.eclipse.lsp4j.debug.SetBreakpointsArguments
import org.eclipse.lsp4j.debug.SetBreakpointsResponse
import org.eclipse.lsp4j.debug.StackFrame
import org.eclipse.lsp4j.debug.StackTraceArguments
import org.eclipse.lsp4j.debug.StackTraceResponse
import org.eclipse.lsp4j.debug.StoppedEventArguments
import org.eclipse.lsp4j.debug.ThreadEventArguments
import org.eclipse.lsp4j.debug.VariablesArguments
import org.eclipse.lsp4j.debug.VariablesResponse
import scala.collection.concurrent.TrieMap
import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.meta.inputs.Position
import scala.meta.internal.metals.MetalsEnrichments._
import scala.meta.internal.metals.debug.TestDebugger.Prefix
import scala.meta.io.AbsolutePath

final class TestDebugger(connect: RemoteServer.Listener => RemoteServer)(
    implicit ec: ExecutionContext
) extends RemoteServer.Listener {

  @volatile private var server: RemoteServer = connect(this)
  private var terminationPromise = Promise[Unit]()
  private val outputBuffer = new StringBuilder
  private val prefixes = mutable.Set.empty[Prefix]
  private val breakpoints = new BreakpointCollector(this)

  def initialize: Future[Capabilities] = {
    val arguments = new InitializeRequestArguments
    arguments.setAdapterID("test-adapter")
    server.initialize(arguments).asScala
  }

  def launch: Future[Unit] = {
    server.launch(Collections.emptyMap()).asScala.ignoreValue
  }

  def configurationDone: Future[Unit] = {
    server.configurationDone(new ConfigurationDoneArguments).asScala.ignoreValue
  }

  def start: Future[Unit] = {
    for {
      _ <- launch
      _ <- configurationDone
    } yield ()
  }

  def restart: Future[Unit] = {
    val args = new DisconnectArguments
    args.setRestart(true)
    for {
      _ <- server.disconnect(args).asScala
      _ <- awaitCompletion
    } yield {
      terminationPromise = Promise()
      server = connect(this)
      outputBuffer.clear()
      breakpoints.clear()
    }
  }

  def disconnect: Future[Unit] = {
    val args = new DisconnectArguments
    args.setRestart(false)
    args.setTerminateDebuggee(false)
    server.disconnect(args).asScala.ignoreValue
  }

  def setBreakpoints(
      path: AbsolutePath,
      position: Position
  ): Future[SetBreakpointsResponse] = {
    import tests.DapEnrichments._
    val args = new SetBreakpointsArguments
    args.setSource(path.toDAP)
    args.setBreakpoints(Array(position.toBreakpoint))
    server.setBreakpoints(args).asScala
  }

  /**
   * Not waiting for exited because it might not be sent
   */
  def awaitCompletion: Future[Unit] = {
    for {
      _ <- terminationPromise.future
      _ <- server.listening.onTimeout(20, TimeUnit.SECONDS)(this.close())
    } yield ()
  }

  def awaitOutput(expected: String): Future[Unit] = {
    val prefix = Prefix(expected, Promise())
    prefixes += prefix

    if (output.startsWith(expected)) {
      prefix.promise.success(())
      prefixes -= prefix
    }

    prefix.promise.future
  }

  def output: String = outputBuffer.toString()

  def breakpointUsage = breakpoints()

  def close(): Unit = {
    server.cancel()
    prefixes.foreach { prefix =>
      val message = s"Output did not start with ${prefix.pattern}"
      prefix.promise.failure(new IllegalStateException(message))
    }
  }

  override def onOutput(event: OutputEventArguments): Unit = {
    outputBuffer.append(event.getOutput)
    val out = output
    val matched = prefixes.filter(prefix => out.startsWith(prefix.pattern))
    matched.foreach { prefix =>
      prefixes.remove(prefix)
      prefix.promise.success(())
    }
  }

  override def onTerminated(): Unit = {
    terminationPromise.trySuccess(())
  }

  override def onThread(args: ThreadEventArguments): Unit = {
    println(args)
  }

  override def onBreakpoint(args: BreakpointEventArguments): Unit = {
    import org.eclipse.lsp4j.debug.{BreakpointEventArgumentsReason => Reason}
    val breakpoint = args.getBreakpoint
    args.getReason match {
      case Reason.NEW | Reason.CHANGED =>
        breakpoints.update(breakpoint)
      case Reason.REMOVED =>
        breakpoints.remove(breakpoint)
    }
  }

  override def onStopped(args: StoppedEventArguments): Unit = {
    import org.eclipse.lsp4j.debug.{StoppedEventArgumentsReason => Reason}
    args.getReason match {
      case Reason.BREAKPOINT =>
        breakpoints.onStopped(args.getThreadId)
      case _ =>
        Future.failed(
          new IllegalStateException(s"Unsupported reason ${args.getReason}")
        )
    }
  }
}

object TestDebugger {
  private val timeout = TimeUnit.SECONDS.toMillis(60).toInt

  def apply(uri: URI)(implicit ec: ExecutionContext): TestDebugger = {
    def connect(listener: RemoteServer.Listener): RemoteServer = {
      val socket = new Socket()
      socket.connect(new InetSocketAddress(uri.getHost, uri.getPort), timeout)
      RemoteServer(socket, listener)
    }

    new TestDebugger(connect)
  }

  final case class Prefix(pattern: String, promise: Promise[Unit])
}
