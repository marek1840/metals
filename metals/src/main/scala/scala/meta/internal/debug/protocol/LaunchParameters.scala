package scala.meta.internal.debug.protocol

final case class LaunchParameters(
    cwd: String,
    mainClass: String,
    classpath: Array[String]
)
