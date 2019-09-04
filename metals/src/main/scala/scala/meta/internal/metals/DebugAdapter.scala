package scala.meta.internal.metals
import java.net.{InetSocketAddress, Socket}
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicBoolean

import ch.epfl.scala.bsp4j.{LogMessageParams, MessageType}
import com.microsoft.java.debug.core.adapter.{IProviderContext, ProtocolServer}
import com.microsoft.java.debug.core.protocol.Events.{
  DebugEvent,
  OutputEvent,
  TerminatedEvent
}
import com.microsoft.java.debug.core.protocol.Messages._
import com.microsoft.java.debug.core.protocol.Requests.{Command => DebugCommand}

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.meta.internal.metals.DebugAdapter._
import scala.meta.internal.metals.DebugProtocol._
import scala.meta.internal.metals.MetalsEnrichments._

/**
 * TODO explain what is a request seq
 */
final class DebugAdapter(
    socket: Socket,
    ctx: IProviderContext,
    debuggee: Debuggee
)(implicit ec: ExecutionContext)
    extends ProtocolServer(socket.getInputStream, socket.getOutputStream, ctx)
    with JsonParser
    with Cancelable {
  @volatile private var launchSeq: Int = _
  @volatile private var disconnected = false

  private val isStarted = new AtomicBoolean(false)

  private val bound = Promise[InetSocketAddress]()
  private val debuggeeExited = Promise[Unit]()
  private val debuggingDone = Promise[Unit]()
  private val exitStatus = Promise[ExitStatus]()

  override def run(): Unit = {
    if (isStarted.compareAndSet(false, true)) {
      try {
        debuggee.start
        scala.concurrent.blocking(super.run())
      } finally {
        exitStatus.trySuccess(Terminated)
        closeWithTimeout()
      }
    }
  }

  def listen: Future[ExitStatus] = {
    if (!isStarted.get()) ec.execute(() => run())

    exitStatus.future
  }

  def bind(uri: InetSocketAddress): Unit = {
    // debuggee may become attachable for a short time after disconnecting
    // and we don't want to fail then, hence the "try"
    bound.trySuccess(uri)
  }

  def forwardOutput(log: LogMessageParams): Unit = {
    val category =
      if (log.getType == MessageType.ERROR) OutputEvent.Category.stderr
      else OutputEvent.Category.stdout
    val message = log.getMessage + "\n"

    sendEvent(new OutputEvent(category, message))
  }

  override def dispatchRequest(request: Request): Unit = {
    request match {
      case LaunchRequest() =>
        launchSeq = request.seq
        bound.future
          .onTimeout(10, SECONDS)(sendResponse(debuggeeNotBound(request)))
          .map(AttachRequest(launchSeq, _))
          .map(super.dispatchRequest)

      case DisconnectRequest(args) =>
        disconnected = true

        if (args.restart) {
          exitStatus.success(Restarted)
        }

        debuggee.cancel()
        super.dispatchRequest(request)
      case _ =>
        super.dispatchRequest(request)
    }
  }

  override def sendResponse(response: Response): Unit = {
    val requestSeq = response.request_seq
    response match {
      case AttachResponse() if requestSeq == launchSeq =>
        response.command = DebugCommand.LAUNCH.getName
      case _ => // ignore
    }

    super.sendResponse(response)
  }

  override def sendEvent(debugEvent: DebugEvent): Unit = {
    if (disconnected && debugEvent.`type` != "terminated") ()
    else {
      try super.sendEvent(debugEvent)
      finally {
        debugEvent.`type` match {
          case "exited" =>
            debuggeeExited.success(())
          case "terminated" =>
            debuggingDone.success(())
          case _ => // ignore
        }
      }
    }
  }

  override def cancel(): Unit = {
    exitStatus.trySuccess(Terminated)
    debuggee.cancel()
    closeWithTimeout()
  }

  private def closeWithTimeout(): Unit = {
    val sessionDone =
      if (disconnected) debuggingDone.future
      else Future.sequence(Seq(debuggingDone.future, debuggeeExited.future))

    sessionDone
      .onTimeout(10, SECONDS)(scribe.debug("Closing debug adapter forcefully"))
      .onComplete(_ => socket.close())
  }
}

object DebugAdapter {
  sealed trait ExitStatus
  case object Restarted extends ExitStatus
  case object Terminated extends ExitStatus

  private def debuggeeNotBound(req: Request): Response = {
    val name = DebugCommand.LAUNCH.getName
    failure(req.seq, name, "Debuggee not bound within time limit")
  }

  def apply(socket: Socket, debuggee: Debuggee)(
      implicit ec: ExecutionContext
  ): DebugAdapter = {
    val extensions = DebugAdapterExtensions()
    new DebugAdapter(socket, extensions, debuggee)
  }
}
