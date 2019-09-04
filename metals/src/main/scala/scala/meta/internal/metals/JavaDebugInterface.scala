package scala.meta.internal.metals

import javax.tools.ToolProvider

import scala.util.{Failure, Success, Try}

object JavaDebugInterface {
  lazy val load: Try[Unit] = {
    def initializeJDI() = { Class.forName("com.sun.jdi.Value"); () }
    def loadTools: Try[Unit] = {
      import java.net.URL
      import java.net.URLClassLoader

      val urls = Option(ToolProvider.getSystemToolClassLoader).collect {
        case classLoader: URLClassLoader => classLoader.getURLs
      }

      urls match {
        case None =>
          Failure(
            new Exception("JDI implementation is not provided by the vendor")
          )

        case Some(urls) =>
          val hotLoadTools = Try {
            val method =
              classOf[URLClassLoader].getDeclaredMethod("addURL", classOf[URL])
            method.setAccessible(true)
            urls.foreach(method.invoke(getClass.getClassLoader, _))
            initializeJDI()
          }

          hotLoadTools.recoverWith {
            case cause: ClassNotFoundException =>
              Failure(
                new Exception(
                  "JDI implementation is not on the classpath",
                  cause
                )
              )
            case cause: ReflectiveOperationException =>
              Failure(
                new Exception(
                  "Could not load tools due to: " + cause.getMessage,
                  cause
                )
              )
          }
      }
    }

    Try(initializeJDI()).orElse(loadTools).map(_ => ())
  }
}
