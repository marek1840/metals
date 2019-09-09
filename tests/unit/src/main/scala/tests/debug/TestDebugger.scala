package tests.debug

import java.net.{InetSocketAddress, Socket, URI}
import java.util.Collections
import java.util.concurrent.TimeUnit

import org.eclipse.lsp4j.debug._
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient
import tests.debug.TestDebugger.Prefix

import scala.collection.mutable
import scala.concurrent.{
  ExecutionContext,
  ExecutionContextExecutorService,
  Future,
  Promise
}
import scala.meta.internal.metals.MetalsEnrichments._

final class TestDebugger(connect: IDebugProtocolClient => ServerConnection)(
    implicit ec: ExecutionContext
) extends IDebugProtocolClient
    with AutoCloseable {
  protected var server: ServerConnection = connect(this)

  private var initializedPromise = Promise[Unit]()
  private var terminationPromise = Promise[Unit]()
  private var exitedPromise = Promise[Unit]()
  private val outputs = mutable.Map.empty[String, StringBuilder]
  private val prefixes = mutable.Set.empty[Prefix]

  def initialize: Future[Capabilities] = {
    val arguments = new InitializeRequestArguments
    arguments.setAdapterID("test-adapter")
    server.initialize(arguments).asScala
  }

  def launch: Future[Unit] = {
    for {
      _ <- server.launch(Collections.emptyMap()).asScala
      _ <- initializedPromise.future
      _ <- server.configurationDone(new ConfigurationDoneArguments).asScala
    } yield ()
  }

  def restart: Future[Unit] = {
    val args = new DisconnectArguments
    args.setRestart(true)
    for {
      _ <- server.disconnect(args).asScala
      _ <- server.listening.onTimeout(20, TimeUnit.SECONDS)(server.cancel())
    } yield {
      initializedPromise = Promise()
      terminationPromise = Promise()
      exitedPromise = Promise()
      server = connect(this)
      outputs.clear()
    }
  }

  def disconnect: Future[Unit] = {
    server.disconnect(new DisconnectArguments).asScala.ignoreValue
  }

  /**
   * Not waiting for exited because TODO
   */
  def awaitCompletion: Future[Unit] = {
    for {
      _ <- terminated
      _ <- server.listening.onTimeout(20, TimeUnit.SECONDS)(this.close())
    } yield ()
  }

  def terminated: Future[Unit] = terminationPromise.future
  def exited: Future[Unit] = exitedPromise.future

  def awaitOutput(expected: String): Future[Unit] = {
    val prefix = Prefix(expected, Promise())
    prefixes += prefix

    if (output.startsWith(expected)) {
      prefix.promise.success(())
      prefixes -= prefix
    }

    prefix.promise.future
  }

  ////////////////////////////////////////////////////////
  override def initialized(): Unit = {
    initializedPromise.success(())
  }

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
    val matched = prefixes.filter(prefix => output.startsWith(prefix.pattern))
    matched.foreach { prefix =>
      prefixes.remove(prefix)
      prefix.promise.success(())
    }
  }

  private def output(arg: String): mutable.StringBuilder = {
    outputs.getOrElseUpdate(arg, new mutable.StringBuilder())
  }

  override def close(): Unit = {
    server.cancel()
    prefixes.foreach { prefix =>
      val message = s"Output did not start with ${prefix.pattern}"
      prefix.promise.failure(new IllegalStateException(message))
    }
  }
}

object TestDebugger {
  def apply(
      uri: URI
  )(implicit ec: ExecutionContextExecutorService): TestDebugger = {
    def connect(client: IDebugProtocolClient) = {
      val socket = new Socket()
      socket.connect(new InetSocketAddress(uri.getHost, uri.getPort), 2000)
      ServerConnection.open(socket, ClientProxy(client))
    }

    val debugger = new TestDebugger(connect)

    debugger
  }

  final case class Prefix(pattern: String, promise: Promise[Unit])
}
