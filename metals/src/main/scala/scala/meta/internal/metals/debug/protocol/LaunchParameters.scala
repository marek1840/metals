package scala.meta.internal.metals.debug.protocol

final case class LaunchParameters(
    mainClass: String,
    file: String
)
