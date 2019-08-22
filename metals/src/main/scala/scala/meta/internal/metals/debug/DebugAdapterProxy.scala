package scala.meta.internal.metals.debug
import ch.epfl.scala.{bsp4j => b}
import com.google.gson.{Gson, JsonObject}

import scala.util.{Failure, Try}

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
