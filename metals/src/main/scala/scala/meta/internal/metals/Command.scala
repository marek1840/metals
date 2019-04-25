package scala.meta.internal.metals

case class Command[Response](
    id: String,
    title: String,
    description: String,
    arguments: String = "`null`"
) {
  def unapply(string: String): Boolean = string == id
}
