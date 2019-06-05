object CreateFileInWorkingDir {
  def main(args: Array[String]): Unit = {
    val workspace = Paths.get("").toAbsolutePath
    println(workspace.toString)
  }
}