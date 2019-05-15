package scala.meta.internal.metals.debug.protocol

final case class LaunchParameters(
    cwd: String,
    mainClass: String,
    file: String
)
