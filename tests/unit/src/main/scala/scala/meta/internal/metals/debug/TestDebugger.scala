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
import scala.meta.internal.metals.debug.Breakpoints.StackFrame
import scala.meta.internal.metals.debug.Debuggee.ThreadId
import scala.meta.internal.metals.debug.TestDebugger.Prefix
import scala.meta.io.AbsolutePath

final class TestDebugger(
    connect: RemoteServer.Listener => RemoteServer,
    breakpointHandler: RemoteServer => BreakpointHandler
)(
    implicit ec: ExecutionContext
) extends RemoteServer.Listener {

  @volatile private var server: RemoteServer = connect(this)
  @volatile private var terminationPromise = Promise[Unit]()
  @volatile private var debuggeeStopped = Promise[StackFrame]()
//  @volatile private var debuggeeListener = Breakpoints.

  private val outputBuffer = new StringBuilder
  private val prefixes = mutable.Set.empty[Prefix]
  private val breakpoints = breakpointHandler(server)

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

  def onBreakpoint(
      breakpointHandler: RemoteServer => Breakpoints.Listener
  ): Future[Unit] = {
    val handler = breakpointHandler(server)
    debuggeeStopped.future.flatMap(handler.onStopped)
    ???
  }

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
        frame(args.getThreadId)
          .map(debuggeeStopped.success)
          .onComplete { _ =>
            debuggeeStopped = Promise()
          }
      case _ =>
        Future.failed(
          new IllegalStateException(s"Unsupported reason ${args.getReason}")
        )
    }
  }

  def frame(threadId: Long): Future[StackFrame] = {
    def stackTrace(thread: Long): Future[StackTraceResponse] = {
      val args = new StackTraceArguments
      args.setThreadId(thread)
      args.setLevels(1L)
      server.stackTrace(args).asScala
    }

    def scopes(frame: Long): Future[ScopesResponse] = {
      val args = new ScopesArguments
      args.setFrameId(frame)
      server.scopes(args).asScala
    }

    def variables(id: Long): Future[VariablesResponse] = {
      val args = new VariablesArguments
      args.setVariablesReference(id)
      server.variables(args).asScala
    }

    for {
      frame <- stackTrace(threadId).map(_.getStackFrames.head)
      scopes <- scopes(frame.getId).map(_.getScopes)
      variables <- {
        val scopeVariables = scopes.map { scope =>
          variables(scope.getVariablesReference).map { response =>
            val variables = response.getVariables
              .map(v => Variable(v.getName, v.getType, v.getValue))
              .toList

            scope.getName -> variables
          }
        }

        Future
          .sequence(scopeVariables.toList)
          .map(scopes => Variables(scopes.toMap))
      }
    } yield StackFrame(threadId, frame, variables)
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

    new TestDebugger(connect, ???)
  }

  final case class Prefix(pattern: String, promise: Promise[Unit])
}
