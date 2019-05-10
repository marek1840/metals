package scala.meta.internal.metals.debug
import ch.epfl.scala.bsp4j.BuildTargetIdentifier
import scala.meta.internal.metals.debug.{protocol => metals}
import scala.meta.internal.debug.{protocol => debug}
import scala.meta.internal.metals.BuildTargets
import scala.meta.internal.metals.MetalsEnrichments._

final class LaunchArgsAdapter(buildTargets: BuildTargets) {
  def adapt(
      args: metals.LaunchParameters
  ): Option[debug.LaunchParameters] = {
    for {
      buildTarget <- buildTargets.inverseSources(args.file.toAbsolutePath)
      classpath = classpathOf(buildTarget)
    } yield debug.LaunchParameters(args.mainClass, classpath)
  }

  private def classpathOf(buildTarget: BuildTargetIdentifier): Array[String] =
    for {
      dependency <- buildTargets.scalacOptions(buildTarget).toArray
      classpath <- dependency.getClasspath.asScala
    } yield classpath
}
