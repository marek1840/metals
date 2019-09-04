package scala.meta.internal.metals
import java.net.{InetSocketAddress, ServerSocket, URI}
import java.util.UUID
import java.util.concurrent.{CompletableFuture, TimeUnit}
import java.util.concurrent.atomic.AtomicBoolean

import ch.epfl.scala.bsp4j.LogMessageParams
import com.google.common.net.InetAddresses

import scala.collection.concurrent.TrieMap
import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Try}
import scala.meta.internal.metals.MetalsEnrichments._

final class DebugAdapters(implicit ec: ExecutionContext) extends Cancelable {
  private val isCanceled = new AtomicBoolean(false)
  private val adapters = TrieMap.empty[String, DebugAdapter]

  def startAdapter(debuggeeFactory: String => Debuggee): Try[URI] = {
    if (!isCanceled.get()) {
      val server = DebugAdapters.startServer()

      def listen: Future[Unit] = {
        Future(server.accept())
          .onTimeout(10, TimeUnit.SECONDS)(server.close())
          .flatMap { client =>
            val id = UUID.randomUUID().toString
            val debuggee = debuggeeFactory(id)
            val adapter = DebugAdapter(client, debuggee)
            adapters += (id -> adapter)

            adapter.listen.flatMap {
              case DebugAdapter.Restarted => listen
              case DebugAdapter.Terminated => Future.unit
            }
          }
      }

      listen.onComplete(_ => server.close())

      val host = InetAddresses.toUriString(server.getInetAddress)
      val port = server.getLocalPort
      Try(URI.create(s"tcp://$host:$port"))
    } else {
      Failure(new IllegalStateException("Debug adapter repository is closed"))
    }
  }

  def bind(id: String, uri: URI): Unit = {
    if (!isCanceled.get()) {
      val adapter = adapters(id)
      if (adapter == null) {
        val msg = s"Could not bind adapter [$id] to [$uri]: no such adapter"
        scribe.warn(msg)
      } else {
        val address = new InetSocketAddress(uri.getHost, uri.getPort)
        adapter.bind(address)
      }
    }
  }

  def forwardOutput(id: String, log: LogMessageParams): Unit = {
    if (!isCanceled.get()) {
      val adapter = adapters(id)
      if (adapter == null) {
        val msg = s"Could not forward output to adapter [$id]"
        scribe.warn(msg)
      } else {
        adapter.forwardOutput(log)
      }
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
  private val Backlog = 2
  private val ServerTimeout = TimeUnit.SECONDS.toMillis(10).toInt

  def startServer(): ServerSocket = {
    val server = new ServerSocket(AnyPort, Backlog)
    server.setSoTimeout(ServerTimeout)
    server
  }
}
