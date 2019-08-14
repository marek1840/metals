package scala.meta.internal.metals.debug
import java.util.concurrent.CompletableFuture

import org.eclipse.lsp4j.debug._
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer

trait ServerProxy extends IDebugProtocolServer {
  protected[this] def requestFromServer[A](
      f: IDebugProtocolServer => CompletableFuture[A]
  ): CompletableFuture[A]

  override def runInTerminal(
      args: RunInTerminalRequestArguments
  ): CompletableFuture[RunInTerminalResponse] =
    requestFromServer(_.runInTerminal(args))

  override def initialize(
      args: InitializeRequestArguments
  ): CompletableFuture[Capabilities] =
    requestFromServer(_.initialize(args))

  override def configurationDone(
      args: ConfigurationDoneArguments
  ): CompletableFuture[Void] =
    requestFromServer(_.configurationDone(args))

  override def launch(
      args: java.util.Map[String, AnyRef]
  ): CompletableFuture[Void] =
    requestFromServer(_.launch(args))

  override def attach(
      args: java.util.Map[String, AnyRef]
  ): CompletableFuture[Void] = {
    requestFromServer(_.attach(args))
  }

  override def restart(args: RestartArguments): CompletableFuture[Void] =
    requestFromServer(_.restart(args))

  override def disconnect(args: DisconnectArguments): CompletableFuture[Void] =
    requestFromServer(_.disconnect(args))

  override def terminate(args: TerminateArguments): CompletableFuture[Void] =
    requestFromServer(_.terminate(args))

  override def setBreakpoints(
      args: SetBreakpointsArguments
  ): CompletableFuture[SetBreakpointsResponse] =
    requestFromServer(_.setBreakpoints(args))

  override def setFunctionBreakpoints(
      args: SetFunctionBreakpointsArguments
  ): CompletableFuture[SetFunctionBreakpointsResponse] =
    requestFromServer(_.setFunctionBreakpoints(args))

  override def setExceptionBreakpoints(
      args: SetExceptionBreakpointsArguments
  ): CompletableFuture[Void] =
    requestFromServer(_.setExceptionBreakpoints(args))

  override def continue_(
      args: ContinueArguments
  ): CompletableFuture[ContinueResponse] =
    requestFromServer(_.continue_(args))

  override def next(args: NextArguments): CompletableFuture[Void] =
    requestFromServer(_.next(args))

  override def stepIn(args: StepInArguments): CompletableFuture[Void] =
    requestFromServer(_.stepIn(args))

  override def stepOut(args: StepOutArguments): CompletableFuture[Void] =
    requestFromServer(_.stepOut(args))

  override def stepBack(args: StepBackArguments): CompletableFuture[Void] =
    requestFromServer(_.stepBack(args))

  override def reverseContinue(
      args: ReverseContinueArguments
  ): CompletableFuture[Void] =
    requestFromServer(_.reverseContinue(args))

  override def restartFrame(
      args: RestartFrameArguments
  ): CompletableFuture[Void] =
    requestFromServer(_.restartFrame(args))

  override def goto_(args: GotoArguments): CompletableFuture[Void] =
    requestFromServer(_.goto_(args))

  override def pause(args: PauseArguments): CompletableFuture[Void] =
    requestFromServer(_.pause(args))

  override def stackTrace(
      args: StackTraceArguments
  ): CompletableFuture[StackTraceResponse] =
    requestFromServer(_.stackTrace(args))

  override def scopes(
      args: ScopesArguments
  ): CompletableFuture[ScopesResponse] =
    requestFromServer(_.scopes(args))

  override def variables(
      args: VariablesArguments
  ): CompletableFuture[VariablesResponse] =
    requestFromServer(_.variables(args))

  override def setVariable(
      args: SetVariableArguments
  ): CompletableFuture[SetVariableResponse] =
    requestFromServer(_.setVariable(args))

  override def source(
      args: SourceArguments
  ): CompletableFuture[SourceResponse] =
    requestFromServer(_.source(args))

  override def threads(): CompletableFuture[ThreadsResponse] =
    requestFromServer(_.threads())

  override def terminateThreads(
      args: TerminateThreadsArguments
  ): CompletableFuture[Void] =
    requestFromServer(_.terminateThreads(args))

  override def modules(
      args: ModulesArguments
  ): CompletableFuture[ModulesResponse] =
    requestFromServer(_.modules(args))

  override def loadedSources(
      args: LoadedSourcesArguments
  ): CompletableFuture[LoadedSourcesResponse] =
    requestFromServer(_.loadedSources(args))

  override def evaluate(
      args: EvaluateArguments
  ): CompletableFuture[EvaluateResponse] =
    requestFromServer(_.evaluate(args))

  override def setExpression(
      args: SetExpressionArguments
  ): CompletableFuture[SetExpressionResponse] =
    requestFromServer(_.setExpression(args))

  override def stepInTargets(
      args: StepInTargetsArguments
  ): CompletableFuture[StepInTargetsResponse] =
    requestFromServer(_.stepInTargets(args))

  override def gotoTargets(
      args: GotoTargetsArguments
  ): CompletableFuture[GotoTargetsResponse] =
    requestFromServer(_.gotoTargets(args))

  override def completions(
      args: CompletionsArguments
  ): CompletableFuture[CompletionsResponse] =
    requestFromServer(_.completions(args))

  override def exceptionInfo(
      args: ExceptionInfoArguments
  ): CompletableFuture[ExceptionInfoResponse] =
    requestFromServer(_.exceptionInfo(args))
}
