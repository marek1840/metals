package scala.meta.internal.metals

import scala.collection.mutable
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

object TryAll {
  def apply(ops: (() => Unit)*): Try[Unit] = {
    val exceptions = mutable.ListBuffer.empty[Throwable]
    ops.foreach { op =>
      try op()
      catch {
        case NonFatal(e) => exceptions += e
      }
    }

    val exception = exceptions.reduceOption { (suppressed, suppressor) =>
      suppressor.addSuppressed(suppressed)
      suppressor
    }

    exception match {
      case None => Success(())
      case Some(e) => Failure(e)
    }
  }
}
