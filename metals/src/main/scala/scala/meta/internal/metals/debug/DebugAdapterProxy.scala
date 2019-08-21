package scala.meta.internal.metals.debug
import java.net.{InetSocketAddress, ServerSocket, Socket}
import java.util.Collections
import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}
import java.util.concurrent.{CompletableFuture, TimeUnit}

import ch.epfl.scala.bsp4j.DebugSessionParams
import ch.epfl.scala.{bsp4j => b}
import com.google.gson.{Gson, JsonObject}
import org.eclipse.lsp4j.debug._

import scala.concurrent.{ExecutionContextExecutorService, Future, Promise}
import scala.meta.internal.metals.MetalsEnrichments._
import scala.meta.internal.metals.{BuildServerConnection, Cancelable}
import scala.util.{Failure, Success, Try}

object DebugAdapterProxy {
  private val gson = new Gson()
  def parseParameters(arguments: Seq[Any]): Try[b.DebugSessionParams] =
    arguments match {
      case Seq(params: JsonObject) =>
        Try(gson.fromJson(params, classOf[b.DebugSessionParams]))
      case _ =>
        Failure(new IllegalArgumentException(s"arguments: $arguments"))
    }
}
