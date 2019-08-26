package tests.debug
import java.net.{InetSocketAddress, Socket, SocketException, URI}
import java.lang.Thread
import java.util.Collections
import java.util.concurrent.TimeUnit

import org.eclipse.lsp4j.debug.services.IDebugProtocolClient
import org.eclipse.lsp4j.debug._

import scala.collection.mutable
import scala.concurrent.{
  ExecutionContext,
  ExecutionContextExecutorService,
  Future,
  Promise,
  TimeoutException
}
import scala.meta.internal.metals.Cancelable
import scala.meta.internal.metals.MetalsEnrichments._
import scala.meta.internal.metals.debug.{ClientProxy, ServerConnection}

final class TestDebugger(implicit ec: ExecutionContext)
    extends IDebugProtocolClient
    with Cancelable {
  protected var server: ServerConnection = _

  private val terminationPromise = Promise[Unit]()
  private val exitedPromise = Promise[Unit]()
  private val outputs = mutable.Map.empty[String, StringBuilder]

  def initialize: Future[Capabilities] = {
    val arguments = new InitializeRequestArguments
    arguments.setAdapterID("test-adapter")
    server.initialize(arguments).asScala
  }

  def launch: Future[Unit] = {
    for {
      _ <- server.launch(Collections.emptyMap()).asScala
      _ <- server.configurationDone(new ConfigurationDoneArguments).asScala
    } yield ()
  }

  def restart: Future[Unit] = {
    for {
      _ <- server.restart(new RestartArguments).asScala
      _ <- server.configurationDone(new ConfigurationDoneArguments).asScala
    } yield ()
  }

  def awaitCompletion: Future[Unit] = {
    for {
      _ <- terminated
      _ <- exited
      _ <- server.listening.onTimeout(20, TimeUnit.SECONDS)(this.cancel())
    } yield ()
  }

  def terminated: Future[Unit] = terminationPromise.future
  def exited: Future[Unit] = exitedPromise.future

  ////////////////////////////////////////////////////////
  override def terminated(args: TerminatedEventArguments): Unit = {
    terminationPromise.success(())
  }

  override def exited(args: ExitedEventArguments): Unit = {
    exitedPromise.success(())
  }

  def output: String = {
    output(OutputEventArgumentsCategory.STDOUT).toString()
  }

  override def output(args: OutputEventArguments): Unit = {
    output(args.getCategory).append(args.getOutput)
  }

  private def output(arg: String): mutable.StringBuilder = {
    outputs.getOrElseUpdate(arg, new mutable.StringBuilder())
  }

  override def cancel(): Unit = {
    server.cancel()
  }
}

object TestDebugger {
  def apply(
      uri: URI
  )(implicit ec: ExecutionContextExecutorService): TestDebugger = {
    val socket = new Socket()
    socket.connect(new InetSocketAddress(uri.getHost, uri.getPort), 2000)

    val debugger = new TestDebugger()

    val connection = ServerConnection.open(socket, ClientProxy(debugger))
    debugger.server = connection

    Runtime.getRuntime.addShutdownHook(new Thread(() => debugger.cancel()))

    debugger
  }
}
