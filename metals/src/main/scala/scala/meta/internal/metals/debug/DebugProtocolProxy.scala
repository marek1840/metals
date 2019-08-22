package scala.meta.internal.metals.debug

import java.net.Socket
import java.util.concurrent.ExecutorService

import org.eclipse.lsp4j.jsonrpc.Launcher
import org.eclipse.lsp4j.jsonrpc.debug.DebugLauncher

import scala.reflect.{ClassTag, classTag}

object DebugProtocolProxy {
  def builder[A: ClassTag](socket: Socket, service: Any)(
      implicit executor: ExecutorService
  ): Launcher.Builder[A] = {
    new DebugLauncher.Builder[A]
      .setRemoteInterface(classTag[A].runtimeClass.asInstanceOf[Class[A]])
      .validateMessages(true)
      .setExecutorService(executor)
      .setInput(socket.getInputStream)
      .setOutput(socket.getOutputStream)
      .setLocalService(service)
  }

}
