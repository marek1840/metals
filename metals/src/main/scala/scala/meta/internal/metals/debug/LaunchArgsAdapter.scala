package scala.meta.internal.metals.debug

import ch.epfl.scala.{bsp4j => b}
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.meta.internal.debug.{protocol => jvm}
import scala.meta.internal.metals.BuildTargets
import scala.meta.internal.metals.MetalsEnrichments._
import scala.meta.internal.metals.debug.protocol.LaunchParameters
import scala.meta.internal.metals.debug.{protocol => metals}
import scala.meta.io.AbsolutePath

final class LaunchArgsAdapter(
    compile: Seq[AbsolutePath] => Future[b.CompileResult],
    buildTargets: BuildTargets
)(implicit ec: ExecutionContext) {

  def adapt(
      params: metals.LaunchParameters
  ): Future[jvm.LaunchParameters] = {
    val path = params.file.toAbsolutePath
    for {
      result <- compile(List(path))
      _ <- verify(result)
      buildTarget <- buildTargetFor(path)
    } yield adapt(params, buildTarget)
  }

  private def verify(result: b.CompileResult): Future[Unit] =
    if (result.getStatusCode == b.StatusCode.ERROR)
      Future.failed(new IllegalStateException("Compilation failed"))
    else Future.successful(())

  private def buildTargetFor(path: AbsolutePath) =
    Future {
      buildTargets
        .inverseSources(path)
        .getOrThrow(
          new IllegalStateException(s"Missing build target for $path")
        )
    }

  private def adapt(
      params: LaunchParameters,
      buildTarget: b.BuildTargetIdentifier
  ): jvm.LaunchParameters = {
    val classpath = classpathOf(buildTarget)
    jvm.LaunchParameters(
      params.cwd,
      params.mainClass,
      classpath
    )
  }

  private def classpathOf(buildTarget: b.BuildTargetIdentifier): Array[String] =
    for {
      dependency <- buildTargets.scalacOptions(buildTarget).toArray
      classpath <- dependency.getClasspath.asScala
    } yield classpath
}
