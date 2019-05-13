package scala.meta.internal.debug

import java.util.concurrent.CompletableFuture
import ch.epfl.scala.{bsp4j => bsp}
import org.eclipse.lsp4j.debug
import org.eclipse.lsp4j.debug.TerminatedEventArguments
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.meta.internal.debug.protocol.ExitedEventArguments
import scala.meta.internal.debug.protocol.LaunchParameters
import scala.meta.internal.metals.MetalsEnrichments._

// Note: Inheriting from [[IDebugProtocolServer]] causes duplicate methods as the rpc
// scans an interface first and then fails for respective methods in the implementation
final class ScalaDebugAdapter(
    implicit val ex: ExecutionContext
) extends DebugAdapter {
  private val client = new ClientDelegate
  private val sessionFactory = new DebugSessionFactory(client)

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
    if (params.noDebug == true) {
      val session = sessionFactory.create(params)

      session.exitCode.map { exitCode =>
        client.terminated(new TerminatedEventArguments)
        client.exited(ExitedEventArguments(exitCode))
      }

      CompletableFuture.completedFuture(null)
    } else {
      val message =
        "Debugging not supported. Please ensure JAVA_HOME points to JDK rather than JRE."
      Future.failed(new IllegalStateException(message)).asJava
    }
  }

  override def setClient(client: IDebugProtocolClient): Unit = {
    this.client.setUnderlying(client)
  }
}
