package scala.meta.internal.metals
import java.util.Collections.singletonList
import java.util.concurrent.{CompletableFuture, TimeUnit}

import ch.epfl.scala.bsp4j.{LaunchParametersDataKind, RunParams, TestParams}

import scala.meta.internal.metals.MetalsEnrichments._
import scala.util.{Failure, Try}

final class Debuggee(handle: () => CompletableFuture[_]) extends Cancelable {
  // must be an actual future obtained from [[BuildServerConnection]].
  // Otherwise cancelling it won't work
  lazy val start: CompletableFuture[_] = handle()

  override def cancel(): Unit = start.cancel(true)
}

object Debuggee {
  def factory(
      sessionParams: DebugSessionParameters
  )(connection: BuildServerConnection): Try[String => Debuggee] = {
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

  // doesn't let the debuggee hang if communication fails for any reason
  private val jdiTimeout = {
    val timeout = TimeUnit.SECONDS.toMillis(10)
    s"timeout=$timeout"
  }

  // address 0.0.0.0 - all IP addresses on all interfaces on the systems
  // port 0 - lets the debuggee select the port
  // the resulting address will be sent from the BSP and bound to the actual adapter
  private val jdiAddress = "address=0.0.0.0:0"

  // suspend to avoid losing output (dap client ignores premature output)
  private val jdiSuspend = "suspend=y"

  private val jdiOption =
    s"-agentlib:jdwp=transport=dt_socket,server=y,$jdiSuspend,$jdiTimeout,$jdiAddress"
}
