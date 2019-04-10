package scala.meta.internal.metals

import com.google.gson.JsonPrimitive

/**
 * Extractors for command arguments - those can be sent either as a json or as an unserialized value (e.g. in tests)
 */
object CommandArguments {
  object StringArg {
    def unapply(arg: AnyRef): Option[String] = arg match {
      case json: JsonPrimitive => Some(json.getAsString)
      case string: String => Some(string)
      case _ => None
    }
  }
}
