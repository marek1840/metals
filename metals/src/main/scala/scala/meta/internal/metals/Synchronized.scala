package scala.meta.internal.metals

final class Synchronized[A](@volatile private var value: A) {
  def transform(f: A => A): Unit = synchronized {
    value = f(value)
  }

  def map[B](f: A => B): B = synchronized {
    f(value)
  }

  def get: A = value
}
