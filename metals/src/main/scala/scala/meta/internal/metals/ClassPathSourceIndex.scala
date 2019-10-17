package scala.meta.internal.metals
import scala.collection.concurrent.TrieMap
import scala.meta.io.AbsolutePath

final class ClassPathSourceIndex {
  // classpath location is unique because it corresponds to the compilation unit
  private val index = TrieMap.empty[String, AbsolutePath]

  def register(classPathLocation: String, sourcePath: AbsolutePath): Unit = {
    index.update(classPathLocation, sourcePath)
  }

  def resolve(classPathLocation: String): Option[AbsolutePath] = {
    index.get(classPathLocation)
  }
}
