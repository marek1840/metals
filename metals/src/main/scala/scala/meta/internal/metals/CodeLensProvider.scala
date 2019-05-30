package scala.meta.internal.metals

import java.util
import java.util.Collections
import java.util.Collections._

import ch.epfl.scala.bsp4j.ScalaMainClass
import ch.epfl.scala.{bsp4j => b}
import org.eclipse.{lsp4j => l}

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.meta.internal.metals.CodeLensProvider._
import scala.meta.internal.metals.ClientCommands.StartDebugSession
import scala.meta.internal.metals.MetalsEnrichments._
import scala.meta.internal.mtags.Semanticdbs
import scala.meta.io.AbsolutePath

final class CodeLensProvider(
    buildTargetClasses: BuildTargetClasses,
    buffers: Buffers,
    buildTargets: BuildTargets,
    semanticdbs: Semanticdbs
)(implicit ec: ExecutionContext) {
  def findLenses(path: AbsolutePath): Future[util.List[l.CodeLens]] = {
    buildTargets.indexed.flatMap { _ =>
      buildTargets.inverseSources(path) match {
        case Some(buildTarget) =>
          for {
            classes <- buildTargetClasses.compiledClasses(buildTarget)
            lenses = findLenses(path, buildTarget, classes)
          } yield lenses.asJava
        case _ =>
          Future.successful(emptyList[l.CodeLens]())
      }
    }
  }

  private def findLenses(
      path: AbsolutePath,
      target: b.BuildTargetIdentifier,
      classes: BuildTargetClasses.Classes
  ): Seq[l.CodeLens] = {
    semanticdbs.textDocument(path).documentIncludingStale match {
      case Some(textDocument) =>
        val distance =
          TokenEditDistance.fromBuffer(path, textDocument.text, buffers)

        for {
          occurrence <- textDocument.occurrences
          symbol = occurrence.symbol
          commands = {
            val main = classes.mainClasses
              .get(symbol)
              .map(MainClassLensFactory.commands(target, _))
              .getOrElse(Nil)
            val tests = classes.testSuites
              .get(symbol)
              .map(TestSuitesLensFactory.commands(target, _))
              .getOrElse(Nil)
            main ++ tests
          }
          if commands.nonEmpty
          range <- occurrence.range
            .flatMap(r => distance.toRevised(r.toLSP))
            .toList
          command <- commands
        } yield new l.CodeLens(range, command, null)
      case _ =>
        Nil
    }
  }
}

object CodeLensProvider {
  sealed trait CommandFactory[A] {
    protected def names: List[String]
    protected def dataKind: String

    final def commands(
        target: b.BuildTargetIdentifier,
        data: A
    ): List[l.Command] = {
      val args = new b.DebugSessionParams(
        singletonList(target),
        new b.LaunchParameters(dataKind, data)
      )

      names.map { name =>
        new l.Command(name, StartDebugSession.id, singletonList(args))
      }
    }

    protected def wrapData(value: A): Any = value
  }

  final object MainClassLensFactory extends CommandFactory[ScalaMainClass] {
    val names: List[String] = List("run")
    val dataKind: String = b.LaunchParametersDataKind.SCALA_MAIN_CLASS
  }

  final object TestSuitesLensFactory extends CommandFactory[String] {
    val names: List[String] = List("test")
    val dataKind: String = b.LaunchParametersDataKind.SCALA_TEST_SUITES

    override protected def wrapData(value: String): Any =
      Collections.singletonList(value)
  }
}
