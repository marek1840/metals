package scala.meta.internal.metals.debug

final case class BreakpointHit(scopes: Map[String, List[Variable]]) {
  override def toString: String = {
    val serializedScopes = scopes.toList
      .sortBy(_._1)
      .map {
        case (scope, variables) =>
          s"$scope:" + variables.sortBy(_.name).map("\n\t" + _)
      }

    serializedScopes.mkString("\n\n")
  }
}
final case class Variable(name: String, `type`: String, value: Variable.Value) {
  override def toString: String = value match {
    case Variable.MemoryReference => s"$name: ${`type`}"
    case Variable.Stringified(value) => s"$name: ${`type`} = $value"
  }
}

object Scope {
  def local(variables: Variable*): (String, List[Variable]) = {
    "Local" -> variables.toList
  }
}

object BreakpointHit {
  def apply(variables: (String, List[Variable])*): BreakpointHit = {
    BreakpointHit(variables.toMap)
  }
}

object Variable {
  sealed trait Value
  case object MemoryReference extends Value
  case class Stringified(override val toString: String) extends Value

  object Value {
    private val memoryReferencePattern = ".*@\\d+".r
    def apply(value: String): Value =
      value match {
        case memoryReferencePattern() => MemoryReference
        case _ => Stringified(value)
      }
  }

  private val stringified = "(.*):(.*)=(.*)".r
  private val typed = "(.*):(.*)".r

  def apply(value: String): Variable = {
    value match {
      case stringified(name, aType, value) =>
        Variable(name.trim, aType.trim, value.trim)
      case typed(name, aType) =>
        Variable(name.trim, aType.trim, MemoryReference)
      case _ =>
        throw new IllegalStateException(s"Illegal variable string: $value")
    }
  }

  def apply(name: String, `type`: String, value: String): Variable = {
    new Variable(name, `type`, Value(value))
  }
}
