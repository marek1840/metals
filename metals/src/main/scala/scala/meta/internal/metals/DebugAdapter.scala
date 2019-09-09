package scala.meta.internal.metals
import java.net.{InetSocketAddress, Socket}
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicBoolean

import ch.epfl.scala.bsp4j.{LogMessageParams, MessageType}
import com.microsoft.java.debug.core.adapter.{IProviderContext, ProtocolServer}
import com.microsoft.java.debug.core.protocol.Events.{DebugEvent, OutputEvent}
import com.microsoft.java.debug.core.protocol.Messages._
import com.microsoft.java.debug.core.protocol.Requests.{Command => DebugCommand}

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.meta.internal.metals.DebugAdapter._
import scala.meta.internal.metals.DebugProtocol._
import scala.meta.internal.metals.MetalsEnrichments._

final class DebugAdapter(
    socket: Socket,
    ctx: IProviderContext,
    debuggee: Debuggee
)(implicit ec: ExecutionContext)
    extends ProtocolServer(socket.getInputStream, socket.getOutputStream, ctx)
    with Cancelable {
  @volatile private var launchSeq: Int = _
  @volatile private var disconnected = false

  private val isStarted = new AtomicBoolean(false)

  private val bound = Promise[InetSocketAddress]()
  private val exitStatus = Promise[ExitStatus]()
  private val remainingTerminalEvents = mutable.TreeSet("terminated", "exited")
  private val debuggingDone = Promise[Unit]()

  override def run(): Unit = {
    if (isStarted.compareAndSet(false, true)) {
      ec.execute(() => {
        try scala.concurrent.blocking(super.run())
        finally exitStatus.trySuccess(Terminated)
      })

      debuggee.start.asScala.onComplete { _ =>
        closeWithTimeout()
      }
    }
  }

  def listen: Future[ExitStatus] = {
    run()
    exitStatus.future
  }

  def bind(uri: InetSocketAddress): Unit = {
    // debuggee may become attachable for a short time after disconnecting
    // and we don't want to fail then, hence the "try"
    bound.trySuccess(uri)
  }

  def forwardOutput(log: LogMessageParams): Unit = {
    val message = log.getMessage + "\n"
    val category =
      if (log.getType == MessageType.ERROR) OutputEvent.Category.stderr
      else OutputEvent.Category.stdout

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
        // "exited" event may not be sent if the debugger disconnects from the jvm beforehand
        remainingTerminalEvents.remove("exited")

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
    try {
      val shouldSend = !disconnected || debugEvent.`type` == "terminated"
      if (shouldSend) super.sendEvent(debugEvent)
    } finally {
      val isTerminalEvent = remainingTerminalEvents.contains(debugEvent.`type`)
      if (isTerminalEvent) {
        remainingTerminalEvents.remove(debugEvent.`type`)
        if (remainingTerminalEvents.isEmpty) debuggingDone.success(())
      }
    }
  }

  override def cancel(): Unit = {
    exitStatus.trySuccess(Terminated)
    debuggee.cancel()
    closeWithTimeout()
  }

  private def closeWithTimeout(): Unit = {
    debuggingDone.future
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
