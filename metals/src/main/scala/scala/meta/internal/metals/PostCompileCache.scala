package scala.meta.internal.metals

import java.util.Collections.singletonList
import java.util.concurrent.ConcurrentHashMap
import ch.epfl.scala.{bsp4j => b}
import scala.concurrent.Future
import scala.meta.internal.metals.MetalsEnrichments._

// TODO maybe per build target?
final class PostCompileCache(buildServer: () => Option[BuildServerConnection]) {
  val mainClasses = new ConcurrentHashMap[String, b.ScalaMainClass]()

  def afterCompiled(target: b.BuildTargetIdentifier): Unit = {
    scribe.info(s">> Populating cache after ${target.getUri}")
    clear()

    buildServer() match {
      case Some(connection) =>
        val parameters = new b.ScalaMainClassesParams(singletonList(target))
        connection
          .mainClasses(parameters)
          .thenAccept(initializeMainClasses)
          .thenRun { () =>
            scribe.info(s""">> Cache populated: 
                           |mainClasses: ${mainClasses.keySet()}
                           |""".stripMargin)
          }
      case None =>
        scribe.info(">> No connection")
    }
  }

  private def clear(): Unit = {
    scribe.info(">> Clearing")
    mainClasses.clear()
  }

  private def initializeMainClasses(result: b.ScalaMainClassesResult): Unit = {
    def createObjectSymbol(className: String): String = {
      val isRootPackage = !className.contains(".")
      val symbol = className.replaceAll("\\.", "/") + "."
      scribe.info(s"$className, $isRootPackage, $symbol")
      if (isRootPackage) {
        "_empty_/" + symbol
      } else {
        symbol
      }
    }

    val classes = result.getItems.asScala.flatMap(_.getClasses.asScala)
    scribe.info(">> Classes: " + classes)
    for {
      item <- result.getItems.asScala
      aClass <- item.getClasses.asScala
      objectSymbol = createObjectSymbol(aClass.getClassName)
    } mainClasses.put(objectSymbol, aClass)
  }
}
