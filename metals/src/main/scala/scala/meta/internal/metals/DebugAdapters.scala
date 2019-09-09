package scala.meta.internal.metals
import java.net.{InetSocketAddress, ServerSocket, URI}
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

import ch.epfl.scala.bsp4j.LogMessageParams
import com.google.common.net.InetAddresses

import scala.collection.concurrent.TrieMap
import scala.concurrent.{ExecutionContext, Future}
import scala.meta.internal.metals.MetalsEnrichments._
import scala.util.{Failure, Try}

/**
 * Stores all active debug adapters and allows sending them various
 * notification like the debuggee address or the debuggee output.
 * It doesn't fail when there is no target adapter for those notifications
 * as those can be triggered by actions outside of metals, e.g. manually
 * configuring java to start with the Java Debug Wire Protocol enabled
 */
final class DebugAdapters(implicit ec: ExecutionContext) extends Cancelable {
  private val isCanceled = new AtomicBoolean(false)
  private val adapters = TrieMap.empty[String, DebugAdapter]

  def startAdapter(debuggeeFactory: String => Debuggee): Try[URI] = {
    if (!isCanceled.get()) {
      val server = DebugAdapters.startServer()

      def handleClient(): Future[Unit] = {
        Future(server.accept())
          .onTimeout(10, TimeUnit.SECONDS)(server.close())
          .flatMap { client =>
            val id = UUID.randomUUID().toString
            val debuggee = debuggeeFactory(id)
            val adapter = DebugAdapter(client, debuggee)
            adapters += (id -> adapter)

            val listen = adapter.listen.flatMap {
              case DebugAdapter.Restarted => handleClient()
              case DebugAdapter.Terminated => Future.unit
            }

            listen.andThen { case _ => adapters.remove(id) }.ignoreValue
          }
      }

      handleClient().onComplete(_ => server.close())

      val host = InetAddresses.toUriString(server.getInetAddress)
      val port = server.getLocalPort
      Try(URI.create(s"tcp://$host:$port"))
    } else {
      Failure(new IllegalStateException("Debug adapter repository is closed"))
    }
  }

  def bind(id: String, uri: URI): Unit = {
    if (!isCanceled.get() && adapters.contains(id)) {
      val address = new InetSocketAddress(uri.getHost, uri.getPort)
      adapters(id).bind(address)
    }
  }

  def forwardOutput(id: String, log: LogMessageParams): Unit = {
    if (!isCanceled.get() && adapters.contains(id)) {
      adapters(id).forwardOutput(log)
    }
  }

  def cancel(): Unit = {
    if (isCanceled.compareAndSet(false, true)) {
      Cancelable.cancelAll(adapters.values)
      adapters.clear()
    }
  }
}

object DebugAdapters {
  private val AnyPort = 0
  private val BacklogSize = 2
  private val ServerTimeout = TimeUnit.SECONDS.toMillis(10).toInt

  def startServer(): ServerSocket = {
    val server = new ServerSocket(AnyPort, BacklogSize)
    server.setSoTimeout(ServerTimeout)
    server
  }
}
