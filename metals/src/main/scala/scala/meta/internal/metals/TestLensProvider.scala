package scala.meta.internal.metals

import java.util
import java.util.Collections._
import ch.epfl.scala.bsp4j.ScalaTestClassesItem
import ch.epfl.scala.{bsp4j => b}
import org.eclipse.{lsp4j => l}
import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.meta.internal.metals.CodeLensCommands.RunTest
import scala.meta.internal.metals.MetalsEnrichments._
import scala.meta.internal.metals.TokenEditDistance.fromBuffer
import scala.meta.internal.mtags.Semanticdbs
import scala.meta.io.AbsolutePath

final class TestLensProvider(
    buildTargets: BuildTargets,
    buildServer: BuildServerConnection,
    semanticdbs: Semanticdbs,
    buffers: Buffers
)(implicit ec: ExecutionContext)
    extends CodeLensProvider {

  def findLenses(path: AbsolutePath): Future[util.List[l.CodeLens]] = {
    for {
      classes <- testClassesWithinBuildTarget(path)
      lenses = if (classes.isEmpty) Nil else findLocations(classes, path)
    } yield lenses.asJava
  }

  private def findLocations(
      classes: Map[String, String],
      path: AbsolutePath
  ): List[l.CodeLens] = {
    semanticdbs.textDocument(path).getE match {
      case Right(textDocument) =>
        val distance = fromBuffer(path, textDocument.text, buffers)

        val lenses = for {
          occurrence <- textDocument.occurrences
          if classes.contains(occurrence.symbol)
          testClass = classes(occurrence.symbol)
          range <- occurrence.range
            .map(_.toLSP)
            .flatMap(distance.toRevised)
            .toSeq
          arguments = List(path.toURI.toString, testClass)
        } yield RunTest.lens(range, arguments)
        lenses.toList
      case _ => Nil
    }
  }

  private def testClassesWithinBuildTarget(
      path: AbsolutePath
  ): Future[Map[String, String]] = {
    buildTargets.inverseSources(path) match {
      case None =>
        Future.successful(Map())
      case Some(buildTarget) =>
        val parameters =
          new b.ScalaTestClassesParams(singletonList(buildTarget))
        // TODO remove dummy data when BSP starts supporting the TestClasses request

        val dummy = new b.ScalaTestClassesResult(
          singletonList(
            new ScalaTestClassesItem(buildTarget, singletonList("example.Test"))
          )
        )

        for {
          result <- Future.successful(dummy) //buildServer.testClasses(parameters).asScala
          classes = classesBySymbol(result)
        } yield classes
    }
  }

  private def classesBySymbol(
      result: b.ScalaTestClassesResult
  ): Map[String, String] = {
    val classesBySymbol = mutable.Map.empty[String, String]
    for {
      item <- result.getItems.asScala
      aClass <- item.getClasses.asScala
      objectSymbol = aClass.replaceAll("\\.", "/") + "."
    } classesBySymbol += (objectSymbol -> aClass)

    classesBySymbol.toMap
  }
}
