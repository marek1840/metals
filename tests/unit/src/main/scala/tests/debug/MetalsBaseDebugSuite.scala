package tests.debug

import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializedParams
import org.eclipse.lsp4j.debug.InitializeRequestArguments
import tests.BaseSuite
import tests.QuickBuild
import tests.debug.DebugDirectory._
import scala.concurrent.ExecutionContext.fromExecutorService
import scala.concurrent.ExecutionContextExecutorService
import scala.concurrent.Future
import scala.meta.internal.metals.MetalsEnrichments._
import scala.meta.internal.metals.MetalsLanguageServer
import scala.meta.internal.metals.NoopLanguageClient
import scala.meta.internal.metals.debug.MetalsDebugAdapter

protected[debug] abstract class MetalsBaseDebugSuite extends BaseSuite {
  private val executor: ExecutorService = Executors.newCachedThreadPool()
  protected implicit val executionContext: ExecutionContextExecutorService =
    fromExecutorService(executor)
  private var metalsServer: MetalsLanguageServer = _

  protected var server: DebugProtocolServer = _
  protected var client: DebugProtocolClient = _

  protected final def testDebug(name: String)(
      setUp: InitializeRequestArguments => Unit = _ => {},
      act: DebugProtocolServer => Future[_],
      validate: DebugProtocolClient => Unit
  ): Unit = {
    val arguments = new InitializeRequestArguments()
    setUp(arguments)
    testAsync(name) {
      for {
        _ <- server.initialize(arguments)
        _ <- act(server)
        _ <- client.sessionTermination()
      } yield validate(client)
    }
  }

  override def utestBeforeEach(path: Seq[String]): Unit = {
    initializeWorkspace()
    initializeMetals()
    client = new DebugProtocolClient
    server = {
      val metalsAdapter = MetalsDebugAdapter(
        metalsServer.compilationScheduler.cascadeCompile,
        metalsServer.buildTargets
      )
      new DebugProtocolServer(metalsAdapter)
    }
    server.setClient(client)
  }

  private def initializeWorkspace(): Unit = {
    Files.createDirectories(Paths.get(workspace))

    val content = s"""{ "$moduleName" : {} }"""
    Files.write(Paths.get(metalsJson), content.getBytes(), CREATE)

    QuickBuild.bloopInstall(workspace.toString.toAbsolutePath)
  }

  private def initializeMetals(): Unit = {
    val params = new InitializeParams()
    params.setRootUri(workspace.toString)

    metalsServer = new MetalsLanguageServer(executionContext)

    metalsServer.connectToLanguageClient(NoopLanguageClient)
    metalsServer.initialize(params).get(10, TimeUnit.SECONDS)
    metalsServer.initialized(new InitializedParams).get(10, TimeUnit.SECONDS)
  }

}
