package scala.meta.internal.metals

import ch.epfl.scala.bsp4j.BuildTargetIdentifier
import ch.epfl.scala.{bsp4j => b}

import scala.collection.concurrent.TrieMap
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.meta.internal.metals.BuildTargetClasses.Classes
import scala.meta.internal.metals.MetalsEnrichments._
import scala.meta.internal.semanticdb.Scala.{Descriptor, Symbols}

/**
 * In-memory index of main class symbols grouped by their enclosing build target
 */
final class BuildTargetClasses(
    buildServer: () => Option[BuildServerConnection]
)(implicit val ec: ExecutionContext) {
  private val index =
    new TrieMap[b.BuildTargetIdentifier, Classes]()

  val onStartedCompilation: BatchedFunction[b.BuildTargetIdentifier, Unit] =
    BatchedFunction.fromFunction(invalidateClassesFor)

  val onFinishedCompilation: BatchedFunction[b.BuildTargetIdentifier, Unit] =
    BatchedFunction.fromFuture(fetchClasses)

  def compiledClasses(target: b.BuildTargetIdentifier): Future[Classes] =
    classesOf(target).whenReady

  private def classesOf(target: BuildTargetIdentifier): Classes = {
    index.getOrElseUpdate(target, new Classes)
  }

  private def invalidateClassesFor(
      targets: Seq[b.BuildTargetIdentifier]
  ): Unit = {
    targets.foreach(classesOf(_).invalidate())
  }

  private def fetchClasses(
      targets: Seq[b.BuildTargetIdentifier]
  ): Future[Unit] = {
    buildServer() match {
      case Some(connection) =>
        val targetsList = targets.asJava

        targetsList.forEach(classesOf(_).clear())

        val updateMainClasses = connection
          .mainClasses(new b.ScalaMainClassesParams(targetsList))
          .thenAccept(cacheMainClasses)
          .asScala

        val updateTestSuites = connection
          .testSuites(new b.ScalaTestClassesParams(targetsList))
          .thenAccept(cacheTestSuites)
          .asScala

        for {
          _ <- updateMainClasses
          _ <- updateTestSuites
        } yield {
          targets.foreach(classesOf(_).setReady())
        }

      case None =>
        Future.successful(())
    }
  }

  private def cacheMainClasses(result: b.ScalaMainClassesResult): Unit = {
    for {
      item <- result.getItems.asScala
      target = item.getTarget
      aClass <- item.getClasses.asScala
      objectSymbol = createObjectSymbol(aClass.getClassName)
    } classesOf(target).mainClasses.put(objectSymbol, aClass)
  }

  private def cacheTestSuites(result: b.ScalaTestClassesResult): Unit = {
    for {
      item <- result.getItems.asScala
      target = item.getTarget
      className <- item.getClasses.asScala
      objectSymbol = createObjectSymbol(className)
    } classesOf(target).testSuites.put(objectSymbol, className)
  }

  private def createObjectSymbol(className: String): String = {
    val symbol = className.replaceAll("\\.", "/")
    val isInsideEmptyPackage = !className.contains(".")
    if (isInsideEmptyPackage) {
      Symbols.Global(Symbols.EmptyPackage, Descriptor.Term(symbol))
    } else {
      symbol + "."
    }
  }
}

object BuildTargetClasses {
  final class Classes {
    private val ready = new Synchronized(Promise[Classes]())

    val mainClasses = new TrieMap[String, b.ScalaMainClass]()
    val testSuites = new TrieMap[String, String]()

    def whenReady: Future[Classes] = {
      ready.map(_.future)
    }

    def invalidate(): Unit = {
      ready.transform { promise =>
        if (promise.isCompleted) Promise() else promise
      }
    }

    def setReady(): Unit = ready.transform(_.success(this))

    def clear(): Unit = {
      mainClasses.clear()
      testSuites.clear()
    }
  }
}
