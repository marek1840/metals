package scala.meta.internal.metals
import ch.epfl.scala.bsp4j.CompileParams
import ch.epfl.scala.bsp4j.StatusCode
import ch.epfl.scala.{bsp4j => b}
import scala.collection.concurrent.TrieMap
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.meta.internal.metals.MetalsEnrichments._
import scala.meta.io.AbsolutePath

final class CompilationScheduler(
    workspace: AbsolutePath,
    buildTargets: BuildTargets,
    buildServer: () => Option[BuildServerConnection]
)(implicit ec: ExecutionContext) {
  private val compiler = new Compiler
  private val isCompiling = TrieMap.empty[b.BuildTargetIdentifier, Boolean]
  private var lastCompile: collection.Set[b.BuildTargetIdentifier] = Set.empty

  def currentlyCompiling: Iterable[b.BuildTargetIdentifier] = isCompiling.keys
  def currentlyCompiling(buildTarget: b.BuildTargetIdentifier): Boolean =
    isCompiling.contains(buildTarget)

  def previouslyCompiled: Iterable[b.BuildTargetIdentifier] = lastCompile
  def previouslyCompiled(buildTarget: b.BuildTargetIdentifier): Boolean =
    lastCompile.contains(buildTarget)

  val cascadeCompile =
    new BatchedFunction[AbsolutePath, b.CompileResult](
      compiler.compile(_, cascade = true)
    )

  val compile =
    new BatchedFunction[AbsolutePath, b.CompileResult](
      compiler.compile(_, cascade = false)
    )

  private class Compiler {
    def compile(
        paths: Seq[AbsolutePath],
        cascade: Boolean
    ): CancelableFuture[b.CompileResult] = {
      buildServer() match {
        case Some(build) =>
          compile(build, paths.filter(compilable), cascade)
        case None =>
          val result = new b.CompileResult(StatusCode.CANCELLED)
          Future.successful(result).asCancelable
      }
    }

    private def compilable(path: AbsolutePath): Boolean =
      path.isScalaOrJava && !path.isDependencySource(workspace)

    private def compile(
        build: BuildServerConnection,
        paths: Seq[AbsolutePath],
        cascade: Boolean
    ): CancelableFuture[b.CompileResult] = {
      val targets = paths.flatMap(buildTargets.inverseSources).distinct
      if (targets.isEmpty) {
        scribe.warn(s"no build target: ${paths.mkString("\n  ")}")
        val result = new b.CompileResult(StatusCode.CANCELLED)
        Future.successful(result).asCancelable
      } else {
        val allTargets = findAllTargets(targets, cascade)
        val params = new CompileParams(allTargets.asJava)

        targets.foreach(target => isCompiling(target) = true)
        val compilation = build.compile(params)

        // after compilation
        compilation.thenAccept { _ =>
          lastCompile = isCompiling.keySet
          isCompiling.clear()
        }

        CancelableFuture(
          compilation.asScala,
          Cancelable(() => compilation.cancel(false))
        )
      }
    }

    private def findAllTargets(
        targets: Seq[b.BuildTargetIdentifier],
        cascade: Boolean
    ): Seq[b.BuildTargetIdentifier] =
      if (!cascade) targets
      else targets.flatMap(buildTargets.inverseDependencies).distinct
  }

}
