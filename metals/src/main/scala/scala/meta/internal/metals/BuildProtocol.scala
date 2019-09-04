package scala.meta.internal.metals
import ch.epfl.scala.bsp4j._
import com.google.gson.{Gson, JsonElement, JsonObject}

import scala.reflect.{ClassTag, classTag}
import scala.util.{Failure, Success, Try}

object BuildProtocol extends JsonParser {
  def scalaMainClass(data: Any): Try[ScalaMainClass] = {
    data.as[ScalaMainClass]
  }

  def scalaTestParams(data: Any): Try[ScalaTestParams] = {
    data.as[ScalaTestParams]
  }
}
