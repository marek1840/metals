package scala.meta.internal.metals
import java.util
import java.util.Collections._
import java.util.concurrent.CompletableFuture
import ch.epfl.scala.bsp4j.ScalaMainClass
import ch.epfl.scala.bsp4j.ScalaMainClassesItem
import ch.epfl.scala.bsp4j.ScalaMainClassesParams
import ch.epfl.scala.bsp4j.ScalaMainClassesResult
import org.eclipse.lsp4j.CodeLens
import org.eclipse.{lsp4j => l}
import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.meta.internal.metals.MetalsEnrichments._
import scala.meta.internal.metals.TokenEditDistance.fromBuffer
import scala.meta.internal.mtags.Semanticdbs
import scala.meta.io.AbsolutePath

final class RunCodeLensProvider(
    buildTargets: BuildTargets,
    buildServer: BuildServerConnection,
    semanticdbs: Semanticdbs,
    buffers: Buffers
)(implicit ec: ExecutionContext) {

  def findLenses(path: AbsolutePath): Future[util.List[l.CodeLens]] = {
    for {
      classes <- mainClassesWithinBuildTarget(path)
      lenses = if (classes.isEmpty) Nil else findLocations(classes, path)
    } yield lenses.asJava
  }

  private def findLocations(
      classes: Map[String, ScalaMainClass],
      path: AbsolutePath
  ): List[l.CodeLens] = {
    semanticdbs.textDocument(path).getE match {
      case Right(textDocument) =>
        val distance = fromBuffer(path, textDocument.text, buffers)

        val lenses = for {
          occurrence <- textDocument.occurrences
          if classes.contains(occurrence.symbol)
          mainClass = classes(occurrence.symbol)
          range <- occurrence.range
            .map(_.toLSP)
            .flatMap(distance.toRevised)
            .toSeq
          arguments = List(
            "file://" + path.toString(),
            mainClass.getClassName,
            mainClass.getArguments,
            mainClass.getJvmOptions
          )
        } yield
          new l.CodeLens(range, CodeLensCommands.RunCode.toLSP(arguments), null)
        lenses.toList
      case _ => Nil
    }
  }

  private def mainClassesWithinBuildTarget(
      path: AbsolutePath
  ): Future[Map[String, ScalaMainClass]] = {
    buildTargets.inverseSources(path) match {
      case None =>
        Future.successful(Map())
      case Some(buildTarget) =>
        val parameters = new ScalaMainClassesParams(singletonList(buildTarget))
        // TODO remove dummy data when BSP starts supporting the mainClasses request
        val aClass = new ScalaMainClass()
        aClass.setClassName("example.OtherMain")

        val dummy = new ScalaMainClassesResult(
          singletonList(
            new ScalaMainClassesItem(
              buildTarget,
              singletonList(aClass)
            )
          )
        )

        for {
          result <- Future.successful(dummy) //buildServer.mainClasses(parameters).asScala
          classes = classesBySymbol(result)
        } yield classes
    }
  }

  private def classesBySymbol(
      result: ScalaMainClassesResult
  ): Map[String, ScalaMainClass] = {
    val classesBySymbol = mutable.Map.empty[String, ScalaMainClass]
    for {
      item <- result.getItems.asScala
      aClass <- item.getClasses.asScala
      objectSymbol = aClass.getClassName.replaceAll("\\.", "/") + "."
    } classesBySymbol += (objectSymbol -> aClass)

    classesBySymbol.toMap
  }
}
