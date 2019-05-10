package scala.meta.internal.metals

import java.util.Collections
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import ch.epfl.scala.bsp4j.BuildClientCapabilities
import ch.epfl.scala.bsp4j.CompileParams
import ch.epfl.scala.bsp4j.CompileResult
import ch.epfl.scala.bsp4j.InitializeBuildParams
import ch.epfl.scala.bsp4j.InitializeBuildResult
import ch.epfl.scala.bsp4j.RunParams
import ch.epfl.scala.bsp4j.RunResult
import ch.epfl.scala.bsp4j.ScalaMainClassesParams
import ch.epfl.scala.bsp4j.ScalaMainClassesResult
import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContextExecutorService
import scala.concurrent.Future
import scala.meta.internal.metals.MetalsEnrichments._
import scala.meta.internal.pc.InterruptException
import scala.meta.internal.rpc.RPC
import scala.meta.io.AbsolutePath

/**
 * An actively running and initialized BSP connection.
 */
final class BuildServerConnection(
    val server: MetalsBuildServer,
    val name: String,
    displayName: String,
    requests: MutableCancelable
)(implicit ec: ExecutionContext)
    extends Cancelable {

  private val cancelled = new AtomicBoolean(false)

  def compile(params: CompileParams): CompletableFuture[CompileResult] = {
    register(server.buildTargetCompile(params))
  }

  def run(params: RunParams): CompletableFuture[RunResult] = {
    register(server.buildTargetRun(params))
  }

  def mainClasses(
      params: ScalaMainClassesParams
  ): CompletableFuture[ScalaMainClassesResult] = {
    register(server.buildTargetScalaMainClasses(params))
  }

  /** Run build/shutdown procedure */
  def shutdown(): Future[Unit] = Future {
    try {
      server.buildShutdown().get(2, TimeUnit.SECONDS)
      server.onBuildExit()
      // Cancel pending compilations on our side, this is not needed for Bloop.
      cancel()
    } catch {
      case _: TimeoutException =>
        scribe.error(s"timeout: build server '$displayName' during shutdown")
      case InterruptException() =>
      case e: Throwable =>
        scribe.error(s"build shutdown: $displayName", e)
    }
  }

  override def cancel(): Unit = {
    if (cancelled.compareAndSet(false, true)) {
      requests.cancel()
    }
  }

  private def register[T](
      future: CompletableFuture[T]
  ): CompletableFuture[T] = {
    requests.add(future.asCancelable)
    future
  }
}

object BuildServerConnection {

  /**
   * Establishes a new build server connection with the given input/output streams.
   *
   * This method is blocking, doesn't return Future[], because if the `initialize` handshake
   * doesn't complete within a few seconds then something is wrong. We want to fail fast
   * when initialization is not successful.
   */
  def apply(
      name: String,
      workspace: AbsolutePath,
      localClient: AnyRef,
      io: RPC.IO,
      cancelable: Cancelable
  )(
      implicit ec: ExecutionContextExecutorService
  ): BuildServerConnection = {
    val connection = RPC.connect(classOf[MetalsBuildServer], localClient, io) {
      builder =>
        val tracePrinter = GlobalTrace.setupTracePrinter("BSP")
        builder.traceMessages(tracePrinter)
    }

    val server = connection.remote

    val handshake = initialize(workspace, server)
    val displayName = handshake.getDisplayName

    val requests = new MutableCancelable()
      .add(connection)
      .add(connection)

    new BuildServerConnection(server, name, displayName, requests)
  }

  /** Run build/initialize handshake */
  private def initialize(
      workspace: AbsolutePath,
      server: MetalsBuildServer
  ): InitializeBuildResult = {
    val initializeResult = server.buildInitialize(
      new InitializeBuildParams(
        "Metals",
        BuildInfo.metalsVersion,
        BuildInfo.bspVersion,
        workspace.toURI.toString,
        new BuildClientCapabilities(
          Collections.singletonList("scala")
        )
      )
    )
    // Block on the `build/initialize` request because it should respond instantly
    // and we want to fail fast if the connection is not
    val result =
      try {
        initializeResult.get(5, TimeUnit.SECONDS)
      } catch {
        case e: TimeoutException =>
          scribe.error("Timeout waiting for 'build/initialize' response")
          throw e
      }
    server.onBuildInitialized()

    scribe.info(">> BS version:" + result.getVersion)
    result
  }
}
