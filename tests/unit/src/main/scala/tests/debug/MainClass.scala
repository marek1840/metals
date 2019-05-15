package tests.debug

protected[debug] final class MainClass(val name: String)(body: String) {
  override val toString: String = body.stripMargin
  def bytes: Array[Byte] = toString.stripMargin.getBytes()
}
