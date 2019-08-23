package scala.meta.internal.metals

import java.util
import java.util.Collections
import java.util.Collections._

import ch.epfl.scala.bsp4j.ScalaMainClass
import ch.epfl.scala.{bsp4j => b}
import org.eclipse.lsp4j.CodeLens
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
    compilations: Compilations,
    semanticdbs: Semanticdbs
)(implicit ec: ExecutionContext) {
  // code lenses will be refreshed after compilation or when workspace gets indexed
  def findLenses(path: AbsolutePath): Seq[l.CodeLens] = {
    val lenses = buildTargets
      .inverseSources(path)
      .filterNot(compilations.isCurrentlyCompiling)
      .map { buildTarget =>
        val classes = buildTargetClasses.classesOf(buildTarget)
        val lenses = findLenses(path, buildTarget, classes)
        lenses
      }

    lenses.getOrElse(Nil)
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
          if occurrence.role.isDefinition
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
  val Empty: util.List[CodeLens] = emptyList()

  sealed trait CommandFactory[A] {
    protected def names: List[String]
    protected def dataKind: String

    final def commands(
        target: b.BuildTargetIdentifier,
        data: A
    ): List[l.Command] = {
      val args = new b.DebugSessionParams(
        singletonList(target),
        new b.LaunchParameters(dataKind, wrap(data))
      )

      names.map { name =>
        new l.Command(name, StartDebugSession.id, singletonList(args))
      }
    }

    protected def wrap(data: A): Any = data
  }

  final object MainClassLensFactory extends CommandFactory[ScalaMainClass] {
    val names: List[String] = List("run")
    val dataKind: String = b.LaunchParametersDataKind.SCALA_MAIN_CLASS
  }

  final object TestSuitesLensFactory extends CommandFactory[String] {
    val names: List[String] = List("test")
    val dataKind: String = b.LaunchParametersDataKind.SCALA_TEST_SUITES

    override protected def wrap(value: String): Any =
      Collections.singletonList(value)
  }
}
