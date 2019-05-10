package scala.meta.internal.debug.protocol

final case class LaunchParameters(
    mainClass: String,
    classpath: Array[String]
)
