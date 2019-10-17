package scala.meta.internal.metals.debug
import org.eclipse.lsp4j.debug.OutputEventArguments
import org.eclipse.lsp4j.debug.{OutputEventArgumentsCategory => Category}
import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.meta.internal.metals.debug.DebuggeeOutput.OutputListener
import scala.meta.internal.metals.debug.DebuggeeOutput.categories

final class DebuggeeOutput {
  private val channels = DebuggeeOutput.channels
  private val listeners = mutable.Map[String, mutable.Buffer[OutputListener]]()

  def get(category: String): String = channels(category).toString()

  def append(event: OutputEventArguments): Unit = synchronized {
    val output = channels(event.getCategory).append(event.getOutput)

    val remaining = listeners
      .getOrElse(event.getCategory, mutable.Buffer.empty)
      .filterNot(_.matches(output.toString()))

    listeners(event.getCategory) = remaining
  }

  def awaitPrefix(category: String, prefix: String): Future[Unit] = {
    if (categories.contains(category)) {
      val promise = Promise[Unit]()
      listeners
        .getOrElseUpdate(category, mutable.Buffer.empty)
        .append(new OutputListener(prefix, promise))
      promise.future
    } else {
      val error =
        s"Unsupported category: $category. Expected one of: $categories"
      Future.failed(new IllegalArgumentException(error))
    }

  }
}

object DebuggeeOutput {
  protected val categories =
    List(Category.CONSOLE, Category.STDERR, Category.STDOUT, Category.TELEMETRY)

  def channels: Map[String, StringBuilder] =
    categories
      .map(name => (name, new StringBuilder))
      .toMap

  protected final class OutputListener(prefix: String, promise: Promise[Unit]) {
    def matches(output: String): Boolean = {
      if (output.startsWith(prefix)) {
        promise.success(())
        true
      } else {
        false
      }
    }
  }
}
