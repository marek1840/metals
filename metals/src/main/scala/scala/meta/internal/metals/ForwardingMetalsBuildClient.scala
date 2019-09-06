package scala.meta.internal.metals

import java.net.URI
import java.util.{Collections, UUID}
import java.util.concurrent.ConcurrentHashMap

import ch.epfl.scala.bsp4j._
import ch.epfl.scala.{bsp4j => b}
import com.google.gson.JsonObject
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification
import org.eclipse.{lsp4j => l}

import scala.collection.concurrent.TrieMap
import scala.concurrent.{ExecutionContext, Promise}
import scala.meta.internal.metals.MetalsEnrichments._
import scala.meta.internal.tvp._
import scala.util.Try
import scala.util.control.NonFatal

/**
 * A build client that forwards notifications from the build server to the language client.
 */
final class ForwardingMetalsBuildClient(
    languageClient: MetalsLanguageClient,
    diagnostics: Diagnostics,
    buildTargets: BuildTargets,
    buildTargetClasses: BuildTargetClasses,
    debugAdapters: DebugAdapters,
    config: MetalsServerConfig,
    statusBar: StatusBar,
    time: Time,
    didCompile: CompileReport => Unit,
    treeViewProvider: () => TreeViewProvider,
    isCurrentlyOpened: b.BuildTargetIdentifier => Boolean
)(implicit ec: ExecutionContext)
    extends MetalsBuildClient
    with Cancelable {

  private case class Compilation(
      timer: Timer,
      promise: Promise[CompileReport],
      isNoOp: Boolean,
      progress: TaskProgress = TaskProgress.empty
  ) extends TreeViewCompilation {
    def progressPercentage = progress.percentage
  }

  private val compilations = TrieMap.empty[BuildTargetIdentifier, Compilation]
  private val hasReportedError = Collections.newSetFromMap(
    new ConcurrentHashMap[BuildTargetIdentifier, java.lang.Boolean]()
  )

  def reset(): Unit = {
    cancel()
  }

  override def cancel(): Unit = {
    for {
      key <- compilations.keysIterator
      compilation <- compilations.remove(key)
    } {
      compilation.promise.cancel()
    }
  }

  def onBuildShowMessage(params: l.MessageParams): Unit =
    languageClient.showMessage(params)

  def onBuildLogMessage(params: b.LogMessageParams): Unit = {
    debugAdapters.forwardOutput(params.getOriginId, params)

    params.getType match {
      case b.MessageType.ERROR =>
        scribe.error(params.getMessage)
      case b.MessageType.WARNING =>
        scribe.warn(params.getMessage)
      case b.MessageType.INFORMATION =>
        scribe.info(params.getMessage)
      case b.MessageType.LOG =>
        scribe.info(params.getMessage)
    }
  }

  def onBuildPublishDiagnostics(params: b.PublishDiagnosticsParams): Unit = {
    diagnostics.onBuildPublishDiagnostics(params)
  }

  def onBuildTargetDidChange(params: b.DidChangeBuildTarget): Unit = {
    scribe.info(params.toString)
  }

  def onBuildTargetCompileReport(params: b.CompileReport): Unit = {}

  @JsonNotification("buildTarget/debuggeeListening")
  def debuggeeListening(address: DebuggeeAddress): Unit = {
    try {
      val uri = URI.create(address.getUri)
      debugAdapters.bind(address.getOriginId, uri)
    } catch {
      case NonFatal(e) =>
        val msg = s"Could not bind debuggee address due to: ${e.getMessage}"
        scribe.error(msg)
    }
  }

  @JsonNotification("build/taskStart")
  def buildTaskStart(params: TaskStartParams): Unit = {
    params.getDataKind match {
      case TaskDataKind.COMPILE_TASK =>
        if (params.getMessage.startsWith("Compiling")) {
          scribe.info(params.getMessage.toLowerCase())
        }
        for {
          task <- params.asCompileTask
          target = task.getTarget
          info <- buildTargets.info(target)
        } {
          diagnostics.onStartCompileBuildTarget(target)
          // cancel ongoing compilation for the current target, if any.
          compilations.remove(target).foreach(_.promise.cancel())

          val name = info.getDisplayName
          val promise = Promise[CompileReport]()
          val isNoOp = params.getMessage.startsWith("Start no-op compilation")
          val compilation = Compilation(new Timer(time), promise, isNoOp)
          compilations(task.getTarget) = compilation

          statusBar.trackFuture(
            s"Compiling $name",
            promise.future,
            showTimer = true,
            progress = Some(compilation.progress)
          )
        }
      case _ =>
    }
  }

  @JsonNotification("build/taskFinish")
  def buildTaskFinish(params: TaskFinishParams): Unit = {
    params.getDataKind match {
      case TaskDataKind.COMPILE_REPORT =>
        for {
          report <- params.asCompileReport
          compilation <- compilations.remove(report.getTarget)
        } {
          diagnostics.onFinishCompileBuildTarget(report.getTarget)
          didCompile(report)
          val target = report.getTarget
          compilation.promise.trySuccess(report)
          val name = buildTargets.info(report.getTarget) match {
            case Some(i) => i.getDisplayName
            case None => report.getTarget.getUri
          }
          val isSuccess = report.getErrors == 0
          val icon = if (isSuccess) config.icons.check else config.icons.alert
          val message = s"${icon}Compiled $name (${compilation.timer})"
          if (!compilation.isNoOp) {
            scribe.info(s"time: compiled $name in ${compilation.timer}")
          }
          if (isSuccess) {
            buildTargetClasses
              .rebuildIndex(target)
              .filter(_ => isCurrentlyOpened(target))
              .foreach(_ => languageClient.refreshModel())

            if (hasReportedError.contains(target)) {
              // Only report success compilation if it fixes a previous compile error.
              statusBar.addMessage(message)
            }
            if (!compilation.isNoOp) {
              treeViewProvider().onBuildTargetDidCompile(report.getTarget())
            }
            hasReportedError.remove(target)
          } else {
            hasReportedError.add(target)
            statusBar.addMessage(
              MetalsStatusParams(
                message,
                command = ClientCommands.FocusDiagnostics.id
              )
            )
          }
        }
      case _ =>
    }
  }

  @JsonNotification("build/taskProgress")
  def buildTaskProgress(params: TaskProgressParams): Unit = {
    params.getDataKind match {
      case "bloop-progress" =>
        for {
          data <- Option(params.getData).collect {
            case o: JsonObject => o
          }
          targetElement <- Option(data.get("target"))
          if targetElement.isJsonObject
          target = targetElement.getAsJsonObject
          uriElement <- Option(target.get("uri"))
          if uriElement.isJsonPrimitive
          uri = uriElement.getAsJsonPrimitive
          if uri.isString
          buildTarget = new BuildTargetIdentifier(uri.getAsString)
          report <- compilations.get(buildTarget)
        } yield {
          report.progress.update(params.getProgress, params.getTotal)
        }
      case _ =>
    }
  }

  def ongoingCompilations(): TreeViewCompilations = new TreeViewCompilations {
    override def get(id: BuildTargetIdentifier) = compilations.get(id)
    override def isEmpty = compilations.isEmpty
    override def size = compilations.size
    override def buildTargets = compilations.keysIterator
  }
}
