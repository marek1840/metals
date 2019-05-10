package scala.meta.internal.bsp
import ch.epfl.scala.bsp4j.BuildClient
import ch.epfl.scala.bsp4j.DidChangeBuildTarget
import ch.epfl.scala.bsp4j.LogMessageParams
import ch.epfl.scala.bsp4j.PublishDiagnosticsParams
import ch.epfl.scala.bsp4j.ShowMessageParams
import ch.epfl.scala.bsp4j.TaskFinishParams
import ch.epfl.scala.bsp4j.TaskProgressParams
import ch.epfl.scala.bsp4j.TaskStartParams

abstract class NoopBuildClient extends BuildClient {
  def onBuildShowMessage(params: ShowMessageParams): Unit = {}
  def onBuildLogMessage(params: LogMessageParams): Unit = {}
  def onBuildTaskStart(params: TaskStartParams): Unit = {}
  def onBuildTaskProgress(params: TaskProgressParams): Unit = {}
  def onBuildTaskFinish(params: TaskFinishParams): Unit = {}
  def onBuildPublishDiagnostics(params: PublishDiagnosticsParams): Unit = {}
  def onBuildTargetDidChange(params: DidChangeBuildTarget): Unit = {}
}
