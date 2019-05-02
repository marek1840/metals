package scala.meta.internal.metals

import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import ch.epfl.scala.{bsp4j => b}
import org.eclipse.{lsp4j => l}
import scala.concurrent.Future
import scala.meta.internal.metals.CodeLensCommands.RunCodeArgs
import scala.meta.internal.metals.MetalsEnrichments._
import scala.meta.io.AbsolutePath

final class CodeRunner(
    buildServer: () => Option[BuildServerConnection],
    buildTargets: BuildTargets,
    languageClient: MetalsLanguageClient
) {
  private val tasks =
    new ConcurrentHashMap[String, CompletableFuture[b.RunResult]]()

  def run(file: AbsolutePath): Future[b.RunResult] = {
    val task = for {
      server <- buildServer()
      id <- buildTargets.inverseSources(file)
    } yield {
      val params = new b.RunParams(id)
      val task = server.run(params)
      register(file, task)
      task.asScala
    }

    task.getOrElse {
      val result = new b.RunResult(b.StatusCode.CANCELLED)
      Future.successful(result)
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
