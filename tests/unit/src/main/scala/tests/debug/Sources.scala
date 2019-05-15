package tests.debug

// TODO abstract over duplicated stuff (declarations of object and main method)
protected[debug] object Sources {
  val PrintHelloWorld: MainClass = new MainClass("PrintHelloWorld")(
    """import java.nio.file.Files
      |import java.nio.file.Paths
      |
      |object PrintHelloWorld {
      |  def main(args: Array[String]): Unit = {
      |    println("Hello, World!")
      |  }
      |}"""
  )

  val CreateFileInWorkingDir: MainClass =
    new MainClass("CreateFileInWorkingDir")(
      """import java.nio.file.Files
        |import java.nio.file.Paths
        |
        |object CreateFileInWorkingDir {
        |  def main(args: Array[String]): Unit = {
        |    val path = Paths.get("out").toAbsolutePath
        |    Files.createFile(path)
        |  }
        |}"""
    )
}
