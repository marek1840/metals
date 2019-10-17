package scala.meta.internal.metals.debug

import tests.DiffAssertions
import scala.collection.mutable
import scala.concurrent.Future
import scala.meta.internal.metals.debug.Stoppage.Cause
import scala.meta.internal.metals.debug.Stoppage.Location
import scala.meta.io.AbsolutePath
import scala.util.control.NonFatal

final class StepNavigator(steps: Seq[(Location, DebugStep)])
    extends Stoppage.Handler {
  private val expectedSteps = mutable.Queue(steps: _*)

  override def apply(frame: StackFrame, cause: Cause): Future[DebugStep] = {
    if (expectedSteps.isEmpty) {
      val error = s"Unexpected $cause stoppage at $frame"
      Future.failed(new IllegalStateException(error))
    } else {
      val (expectedLocation, nextStep) = expectedSteps.dequeue()
      try {
        compare(frame, expectedLocation)
        Future.successful(nextStep)
      } catch {
        case NonFatal(e) =>
          Future.failed(e)
      }
    }
  }

  private def compare(frame: StackFrame, expected: Location) = {
    val info = frame.info
    val actualLocation = s"${info.getSource.getPath}:${info.getLine}"
    val expectedLocation = s"${expected.file}:${expected.line}"
    DiffAssertions.assertNoDiff(actualLocation, expectedLocation)
  }

  def at(path: AbsolutePath, line: Int)(nextStep: DebugStep): StepNavigator = {
    val location = Location(s"file://$path", line)
    new StepNavigator(steps :+ (location -> nextStep))
  }

  def assertAllStepsReached(): Future[Unit] = {
    if (expectedSteps.isEmpty) Future.successful()
    else {
      val remainingSteps = expectedSteps.mkString("\n")
      Future.failed(new IllegalStateException(remainingSteps))
    }
  }
}

object StepNavigator {
  def apply(): StepNavigator = new StepNavigator(Nil)

  final case class StepValidation(file: String, line: Int, next: DebugStep) {
    def onStopped(frame: StackFrame): Future[DebugStep] = {
      Future.successful(next)
    }

    override def toString: String = {
      s"$next at $file:$line"
    }
  }
}
