package scala.meta.internal.metals.debug

import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import org.eclipse.lsp4j.debug.InitializeRequestArgumentsPathFormat
import org.eclipse.lsp4j.debug.SetBreakpointsResponse
import org.eclipse.lsp4j.debug.SourceResponse
import org.eclipse.lsp4j.jsonrpc.MessageConsumer
import org.eclipse.lsp4j.jsonrpc.messages.Message
import org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.meta.internal.metals.Cancelable
import scala.meta.internal.metals.GlobalTrace
import scala.meta.internal.metals.debug.DebugProtocol.InitializeRequest
import scala.meta.internal.metals.debug.DebugProtocol.OutputNotification
import scala.meta.internal.metals.debug.DebugProtocol.RestartRequest
import scala.meta.internal.metals.debug.DebugProtocol.SetBreakpointRequest
import scala.meta.internal.metals.debug.DebugProtocol.SourceRequest
import scala.meta.internal.metals.debug.DebugProxy._

private[debug] final class DebugProxy(
    sourceAdapter: RelativeSourceAdapter,
    sessionName: String,
    client: RemoteEndpoint,
    server: ServerAdapter
)(implicit ec: ExecutionContext) {
  import scala.meta.internal.metals.JsonParser._
  private val exitStatus = Promise[ExitStatus]()
  @volatile private var outputTerminated = false
  private val cancelled = new AtomicBoolean()

  private val breakpointRequestAdapter = new BreakpointRequestAdapter

  lazy val listen: Future[ExitStatus] = {
    scribe.info(s"Starting debug proxy for [$sessionName]")
    listenToServer()
    listenToClient()

    exitStatus.future
  }

  private def listenToClient(): Unit = {
    Future(client.listen(handleClientMessage)).andThen { case _ => cancel() }
  }

  private def listenToServer(): Unit = {
    Future(server.onServerMessage(handleServerMessage)).andThen {
      case _ => cancel()
    }
  }

  private val handleClientMessage: MessageConsumer = {
    case _ if cancelled.get() =>
    // ignore
    case message @ InitializeRequest(args) =>
      if (args.getPathFormat == InitializeRequestArgumentsPathFormat.PATH) {
        breakpointRequestAdapter.adaptPathToURI()
      }

      server.send(message)

    case request @ SetBreakpointRequest(args) =>
      def assembleResponse(
          responses: Iterable[ResponseMessage]
      ): SetBreakpointsResponse = {
        val breakpoints = responses
          .map(DebugProtocol.parseResponse[SetBreakpointsResponse])
          .flatMap(_.toOption)
          .map(_.getBreakpoints)
          .reduceOption(_ ++ _)
          .getOrElse(Array.empty)

        breakpoints.foreach { breakpoint =>
          breakpoint.setSource(args.getSource)
        }

        val response = new SetBreakpointsResponse
        response.setBreakpoints(breakpoints)
        response
      }

      val parts = breakpointRequestAdapter
        .partition(args)
        .map(DebugProtocol.toRequest)

      server
        .sendPartitioned(parts)
        .map(assembleResponse)
        .map(DebugProtocol.toResponse(request.getId, _))
        .foreach(client.consume)

    case RestartRequest(message) =>
      // set the status first, since the server can kill the connection
      exitStatus.trySuccess(Restarted)
      outputTerminated = true
      server.send(message)

    case message =>
      server.send(message)
  }

  private val handleServerMessage: Message => Unit = {
    case _ if cancelled.get() =>
    // ignore
    case OutputNotification(_) if outputTerminated =>
    // ignore. When restarting, the output keeps getting printed for a short while after the
    // output window gets refreshed resulting in stale messages being printed on top, before
    // any actual logs from the restarted process
    case message @ OutputNotification(args) =>
      sourceAdapter.adapt(args.getSource)
      client.consume(message)
    case message @ DebugProtocol.StackTraceResponse(response) =>
      response.getStackFrames.foreach { frame =>
        if (sourceAdapter.adapt(frame.getSource) == false) {
          frame.setSource(null)
        }
      }
      message.setResult(response.toJson)
      client.consume(message)
    case message =>
      client.consume(message)
  }

  def cancel(): Unit = {
    if (cancelled.compareAndSet(false, true)) {
      scribe.info(s"Canceling debug proxy for [$sessionName]")
      exitStatus.trySuccess(Terminated)
      Cancelable.cancelAll(List(client, server))
    }
  }
}

private[debug] object DebugProxy {
  sealed trait ExitStatus
  case object Terminated extends ExitStatus
  case object Restarted extends ExitStatus

  def open(
      name: String,
      awaitClient: () => Future[Socket],
      connectToServer: () => Future[Socket],
      sourceAdapter: RelativeSourceAdapter
  )(implicit ec: ExecutionContext): Future[DebugProxy] = {
    for {
      server <- connectToServer()
        .map(new SocketEndpoint(_))
        .map(endpoint => withLogger(endpoint, "dap-server"))
      client <- awaitClient()
        .map(new SocketEndpoint(_))
        .map(endpoint => withLogger(endpoint, "dap-client"))
    } yield {
      new DebugProxy(
        sourceAdapter,
        name,
        client,
        new ServerAdapter(server)
      )
    }
  }

  private def withLogger(
      endpoint: RemoteEndpoint,
      name: String
  ): RemoteEndpoint = {
    new EndpointLogger(endpoint, GlobalTrace.setup(name))
  }
}
