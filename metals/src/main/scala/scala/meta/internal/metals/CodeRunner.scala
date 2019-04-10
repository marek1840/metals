package scala.meta.internal.metals

import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import ch.epfl.scala.{bsp4j => b}
import org.eclipse.{lsp4j => l}
import scala.concurrent.Future
import scala.meta.internal.metals.MetalsEnrichments._
import scala.meta.io.AbsolutePath

final class CodeRunner(
    buildTargets: BuildTargets,
    languageClient: MetalsLanguageClient
) {
  private val tasks =
    new ConcurrentHashMap[String, CompletableFuture[b.RunResult]]()

  def runCode(
      path: AbsolutePath,
      buildServer: BuildServerConnection
  ): Future[b.RunResult] = {
    val target = buildTargets.inverseSources(path)
    target match {
      case None =>
        val result = new b.RunResult(b.StatusCode.CANCELLED)
        Future.successful(result)
      case Some(id) =>
        val params = new b.RunParams(id)
        val task = buildServer.run(params)
        register(path, task)
        task.asScala
    }
  }

  def cancel(command: String): Boolean = {
    val task = tasks.get(command)
    if (task == null) true
    else task.cancel(true)
  }

  private def register(
      path: AbsolutePath,
      task: CompletableFuture[b.RunResult]
  ): Unit =
    if (!task.isDone) {
      val command = CancelCommand(path)

      tasks.put(command, task)
      languageClient.registerCapability(
        LSPParameters.forCommandRegistration(command)
      )

      // schedule automatic unregistration
      task.whenComplete((_, _) => unregister(command))
    }

  private def unregister(command: String): Unit = {
    tasks.remove(command)
    languageClient.unregisterCapability(
      LSPParameters.forCommandUnregistration(command)
    )
  }
}

object LSPParameters {
  def forCommandRegistration(
      command: String
  ): l.RegistrationParams = {
    val options = new l.ExecuteCommandRegistrationOptions(List(command).asJava)

    val registration =
      new l.Registration(command, "workspace/executeCommand", options)

    new l.RegistrationParams(List(registration).asJava)
  }

  def forCommandUnregistration(command: String): l.UnregistrationParams = {
    val unregistration =
      new l.Unregistration(command, "workspace/executeCommand")

    new l.UnregistrationParams(List(unregistration).asJava)
  }
}

object CancelCommand {
  private val timeFormat = new SimpleDateFormat("HH:mm")
  val prefix = "metals.task.cancel"

  def apply(path: AbsolutePath): String = {
    val timeStamp = timeFormat.format(new Date())
    val name = path.filename.stripSuffix(".scala")
    s"$prefix-$name-$timeStamp"
  }

  def unapply(command: String): Option[String] = {
    if (command.startsWith(prefix)) Some(command)
    else None
  }
}
