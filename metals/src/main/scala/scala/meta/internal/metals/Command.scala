package scala.meta.internal.metals

import org.eclipse.{lsp4j => l}
import MetalsEnrichments._

case class Command[Response](
    id: String,
    title: String,
    description: String,
    arguments: String = "`null`"
) {
  def unapply(string: String): Boolean = string == id

  def toLSP(arguments: List[AnyRef]): l.Command =
    new l.Command(title, id, arguments.asJava)
}
