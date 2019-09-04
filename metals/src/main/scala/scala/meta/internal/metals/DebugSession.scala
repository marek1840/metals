package scala.meta.internal.metals
import java.util.Collections.singletonList
import java.util.concurrent.CompletableFuture

import ch.epfl.scala.bsp4j.{LaunchParametersDataKind, RunParams, TestParams}

import scala.meta.internal.metals.MetalsEnrichments._
import scala.util.{Failure, Try}

final class Debuggee(handle: () => CompletableFuture[_]) extends Cancelable {
  lazy val start: CompletableFuture[Unit] = handle().thenApply(_ => ())

  override def cancel(): Unit = start.cancel(true)
}

object Debuggee {
  def factory(
      connection: BuildServerConnection,
      sessionParams: DebugSessionParameters
  ): Try[String => Debuggee] = {
    sessionParams.dataKind match {
      case LaunchParametersDataKind.SCALA_MAIN_CLASS =>
        BuildProtocol.scalaMainClass(sessionParams.data).map {
          mainClass =>
            { id: String =>
              mainClass.setJvmOptions(singletonList(jdiOption))

              val params = new RunParams(sessionParams.targets.asScala.head)
              params.setOriginId(id)
              params.setDataKind(sessionParams.dataKind)
              params.setData(mainClass)

              new Debuggee(() => connection.run(params))
            }
        }
      case LaunchParametersDataKind.SCALA_TEST_SUITES =>
        BuildProtocol.scalaTestParams(sessionParams.data).map {
          testParams =>
            { id: String =>
              testParams.setJvmOptions(singletonList(jdiOption))

              val params = new TestParams(sessionParams.targets)
              params.setOriginId(id)
              params.setDataKind(sessionParams.dataKind)
              params.setData(testParams)

              new Debuggee(() => connection.test(params))
            }
        }
      case _ =>
        val msg = s"Unsupported data kind: ${sessionParams.dataKind}"
        Failure(new IllegalStateException(msg))
    }
  }

  // suspend to avoid losing output (dap client ignores premature output)
  // TODO include timeout - 10s
  private val jdiOption =
    "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=0.0.0.0:0"
}
