package scala.meta.internal.debug

import java.io.File
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture
import ch.epfl.scala.{bsp4j => bsp}
import org.eclipse.lsp4j.debug
import org.eclipse.lsp4j.debug.TerminatedEventArguments
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest
import scala.concurrent.ExecutionContext
import scala.io.Source.fromInputStream
import scala.meta.internal.debug.protocol.LaunchParameters
import scala.meta.internal.debug.protocol.OutputEventArguments

// Note: Inheriting from IDebugProtocolServer causes duplicate methods as the rpc
// scans an interface first and then fails for respective methods in the implementation

final class ScalaDebugAdapter(
    implicit val ex: ExecutionContext
) extends DebugAdapter {

  private var client: IDebugProtocolClient = new IDebugProtocolClient {}

  private implicit def statusCodeToExitArgs(
      statusCode: bsp.StatusCode
  ): debug.ExitedEventArguments = {
    val args = new debug.ExitedEventArguments
    args.setExitCode(statusCode.getValue.toLong)
    args
  }

  @JsonRequest
  def initialize(
      args: debug.InitializeRequestArguments
  ): CompletableFuture[
    debug.Capabilities
  ] = {
    scribe.info("Initializing scala debug adapter")

    val capabilities = new debug.Capabilities()
    CompletableFuture.completedFuture(capabilities)
  }

  @JsonRequest
  def launch(
      params: LaunchParameters
  ): CompletableFuture[Void] = {
    scribe.info("Launching: " + params)

    val java = Paths
      .get(System.getProperty("java.home"))
      .resolve("bin")
      .resolve("java")
      .toString

    val classpath = {
      params.classpath.mkString(File.pathSeparator)
    }

    val command = Array(java, "-cp", classpath, params.mainClass)
    scribe.info(s"Executing: ${command.mkString(" ")}")
    val process = Runtime.getRuntime.exec(command)
    val i = process.waitFor()

    val output = fromInputStream(process.getInputStream).mkString
    client.output(OutputEventArguments(output))
    client.terminated(new TerminatedEventArguments)
    CompletableFuture.completedFuture(null)
  }

  override def setClient(client: IDebugProtocolClient): Unit = {
    this.client = client
  }
}
