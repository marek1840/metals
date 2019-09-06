package scala.meta.internal.metals

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

import scala.meta.internal.jdk.CollectionConverters._

/** Open collection of cancelables that should cancel together */
final class MutableCancelable extends Cancelable {
  private val isCancelled = new AtomicBoolean(false)
  private val toCancel = new ConcurrentLinkedQueue[Cancelable]()

  def add(cancelable: Cancelable): this.type = {
    if (isCancelled.get()) {
      cancelable.cancel()
    } else {
      toCancel.add(cancelable)
    }
    this
  }

  def addAll(cancelables: Iterable[Cancelable]): this.type = {
    cancelables.foreach(add)
    this
  }

  override def cancel(): Unit = {
    if (isCancelled.compareAndSet(false, true)) {
      Cancelable.cancelAll(toCancel.asScala)
    }
  }
}
