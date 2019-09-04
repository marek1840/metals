package scala.meta.internal.metals

import java.util
import java.util.Collections
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer

import com.microsoft.java.debug.core.IEvaluatableBreakpoint
import com.microsoft.java.debug.core.adapter._
import com.microsoft.java.debug.core.protocol.Types
import com.sun.jdi._
import io.reactivex.Observable

import scala.concurrent.Future

object DebugAdapterExtensions {
  def apply(): IProviderContext = {
    val ctx = new ProviderContext
    ctx.registerProvider(classOf[IEvaluationProvider], EvaluationProvider)
    ctx.registerProvider(
      classOf[IHotCodeReplaceProvider],
      HotCodeReplaceProvider
    )
    ctx.registerProvider(classOf[ICompletionsProvider], CompletionsProvider)
    ctx.registerProvider(classOf[ISourceLookUpProvider], SourceLookUpProvider)
    ctx.registerProvider(
      classOf[IVirtualMachineManagerProvider],
      VirtualMachineManagerProvider
    )
    ctx
  }

  def newContext: IProviderContext = {
    val ctx = new ProviderContext

    ctx.registerProvider(classOf[ISourceLookUpProvider], SourceLookUpProvider)
    ctx.registerProvider(classOf[IEvaluationProvider], EvaluationProvider)
    ctx.registerProvider(classOf[ICompletionsProvider], CompletionsProvider)
    ctx.registerProvider(
      classOf[IHotCodeReplaceProvider],
      HotCodeReplaceProvider
    )
    ctx.registerProvider(
      classOf[IVirtualMachineManagerProvider],
      VirtualMachineManagerProvider
    )

    ctx
  }

  object CompletionsProvider extends ICompletionsProvider {
    override def codeComplete(
        frame: StackFrame,
        snippet: String,
        line: Int,
        column: Int
    ): util.List[Types.CompletionItem] = Collections.emptyList()
  }

  object EvaluationProvider extends IEvaluationProvider {
    override def isInEvaluation(thread: ThreadReference): Boolean = false

    override def evaluate(
        expression: String,
        thread: ThreadReference,
        depth: Int
    ): CompletableFuture[Value] = ???

    override def evaluate(
        expression: String,
        thisContext: ObjectReference,
        thread: ThreadReference
    ): CompletableFuture[Value] = ???

    override def evaluateForBreakpoint(
        breakpoint: IEvaluatableBreakpoint,
        thread: ThreadReference
    ): CompletableFuture[Value] = ???

    override def invokeMethod(
        thisContext: ObjectReference,
        methodName: String,
        methodSignature: String,
        args: Array[Value],
        thread: ThreadReference,
        invokeSuper: Boolean
    ): CompletableFuture[Value] = ???

    override def clearState(thread: ThreadReference): Unit = {}
  }

  object HotCodeReplaceProvider extends IHotCodeReplaceProvider {
    override def onClassRedefined(
        consumer: Consumer[util.List[String]]
    ): Unit = {}
    override def redefineClasses(): CompletableFuture[util.List[String]] =
      CompletableFuture.completedFuture(util.Collections.emptyList())
    override def getEventHub: Observable[HotCodeReplaceEvent] =
      Observable.empty()
  }

  object SourceLookUpProvider extends ISourceLookUpProvider {
    override def supportsRealtimeBreakpointVerification(): Boolean = false

    override def getFullyQualifiedName(
        uri: String,
        lines: Array[Int],
        columns: Array[Int]
    ): Array[String] = Array()

    override def getSourceFileURI(
        fullyQualifiedName: String,
        sourcePath: String
    ): String =
      sourcePath
    override def getSourceContents(uri: String): String = ""
  }

  object VirtualMachineManagerProvider extends IVirtualMachineManagerProvider {
    import com.sun.jdi.{Bootstrap, VirtualMachineManager}
    def getVirtualMachineManager: VirtualMachineManager =
      Bootstrap.virtualMachineManager
  }
}
