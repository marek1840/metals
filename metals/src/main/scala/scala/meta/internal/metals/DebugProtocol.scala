package scala.meta.internal.metals
import java.net.InetSocketAddress

import com.microsoft.java.debug.core.protocol.Events.DebugEvent
import com.microsoft.java.debug.core.protocol.Messages.{Request, Response}
import com.microsoft.java.debug.core.protocol.Requests.{
  AttachArguments,
  DisconnectArguments,
  InitializeArguments,
  Command => DebugCommand
}
import com.microsoft.java.debug.core.protocol.Types.Capabilities
import org.eclipse.lsp4j.debug.ConfigurationDoneArguments

object DebugProtocol extends JsonParser {
  object LaunchRequest {
    def unapply(req: Request): Boolean = {
      req.command == DebugCommand.LAUNCH.getName
    }
  }

  object DisconnectRequest {
    def unapply(req: Request): Option[DisconnectArguments] = {
      Option(req)
        .filter(_.command == DebugCommand.DISCONNECT.getName)
        .flatMap(_.arguments.as[DisconnectArguments].toOption)
    }
  }

  object AttachRequest {
    def apply(seq: Int, address: InetSocketAddress): Request = {
      val args = new AttachArguments()
      args.hostName = address.getHostName
      args.port = address.getPort

      request(seq, DebugCommand.ATTACH, args)
    }
  }

  object AttachResponse {
    def unapply(res: Response): Boolean = {
      res.command == DebugCommand.ATTACH.getName
    }
  }

  def request(seq: Int, command: DebugCommand, args: AnyRef): Request = {
    new Request(seq, command.getName, args.toJsonObject)
  }

  def failure(seq: Int, command: String, message: String): Response = {
    new Response(seq, command, false, message)
  }
}
