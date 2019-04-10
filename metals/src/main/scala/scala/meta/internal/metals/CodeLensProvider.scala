package scala.meta.internal.metals

import java.util
import org.eclipse.{lsp4j => l}
import scala.collection.mutable
import scala.meta._
import scala.meta.inputs.Position
import scala.meta.internal.metals.CodeLensCommands.RunCode
import scala.meta.internal.metals.MetalsEnrichments._
import scala.meta.internal.metals.TokenEditDistance.fromBuffer
import scala.meta.io.AbsolutePath

final class CodeLensProvider(trees: Trees, buffers: Buffers) {
  def getCodeLensesFor(path: AbsolutePath): util.List[l.CodeLens] = {
    val lenses = for {
      tree <- trees.get(path)
      distance = fromBuffer(path, tree.pos.input.text, buffers)
      factory = new CodeLensFactory(distance)
    } yield {
      val lenses = mutable.ListBuffer.empty[l.CodeLens]
      val source = "file://" + path.toString()

      tree.traverse {
        case obj: Defn.Object =>
          obj.templ.stats.collect {
            case Main(pos) =>
              val args = List(source) // TODO should also be a name of the class
              val lens = factory.create(pos, RunCode, args)
              lens.foreach(lenses += _)
          }
      }

      lenses.toList
    }

    lenses.map(_.asJava).getOrElse(util.Collections.emptyList())
  }

  private object Main {
    def unapply(function: Defn.Def): Option[Position] = {
      function.paramss match {
        case List(List(ArrayOfStrings())) if function.name.value == "main" =>
          Some(function.pos)

        case _ =>
          None
      }
    }

    object ArrayOfStrings {
      def unapply(param: Term.Param): Boolean = param.decltpe match {
        case Some(Type.Apply(Type.Name("Array"), List(Type.Name("String")))) =>
          true
        case _ =>
          false
      }
    }
  }

  private final class CodeLensFactory(distance: TokenEditDistance) {
    def create(
        pos: Position,
        command: Command[_],
        arguments: List[AnyRef]
    ): Option[l.CodeLens] =
      for {
        range <- distance.toRevised(pos.toLSP)
      } yield new l.CodeLens(range, command.toLSP(arguments), null)
  }
}
