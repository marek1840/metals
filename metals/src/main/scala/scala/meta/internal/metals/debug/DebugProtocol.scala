package scala.meta.internal.metals.debug

import com.google.gson.JsonElement
import org.eclipse.lsp4j.debug.DisconnectArguments
import org.eclipse.lsp4j.debug.InitializeRequestArguments
import org.eclipse.lsp4j.debug.SetBreakpointsArguments
import org.eclipse.lsp4j.debug.Source
import org.eclipse.lsp4j.jsonrpc.debug.messages.DebugRequestMessage
import org.eclipse.lsp4j.jsonrpc.messages.NotificationMessage
import org.eclipse.lsp4j.jsonrpc.messages.RequestMessage
import scala.util.Success

private[debug] object DebugProtocol {
  import scala.meta.internal.metals.JsonParser._

  object SetBreakpoints {
    def unapply(request: RequestMessage): Option[SetBreakpointsArguments] = {
      if (request.getMethod != "setBreakpoints") None
      else {
        request.getParams match {
          case json: JsonElement =>
            json.as[SetBreakpointsArguments].toOption
          case _ => None
        }
      }
    }
  }

  object RestartRequest {
    def unapply(request: RequestMessage): Option[RequestMessage] = {
      if (request.getMethod != "disconnect") None
      else {
        request.getParams match {
          case json: JsonElement =>
            json.as[DisconnectArguments] match {
              case Success(args) if args.getRestart => Some(request)
              case _ => None
            }
          case _ => None
        }
      }
    }
  }

  object InitializeRequest {
    def unapply(
        response: RequestMessage
    ): Option[InitializeRequestArguments] = {
      if (response.getMethod != "initialize") None
      else {
        response.getParams match {
          case json: JsonElement => json.as[InitializeRequestArguments].toOption
          case _ => None
        }
      }
    }
  }

  object OutputNotification {
    def unapply(notification: NotificationMessage): Boolean = {
      notification.getMethod == "output"
    }
  }

  def toRequest(args: SetBreakpointsArguments): RequestMessage = {
    val request = new DebugRequestMessage
    request.setMethod("setBreakpoints")
    request.setParams(args.toJson)
    request
  }

  def copy(original: Source): Source = {
    val source = new Source
    source.setAdapterData(original.getAdapterData)
    source.setChecksums(original.getChecksums)
    source.setName(original.getName)
    source.setOrigin(original.getOrigin)
    source.setPath(original.getPath)
    source.setPresentationHint(original.getPresentationHint)
    source.setSourceReference(original.getSourceReference)
    source.setSources(original.getSources)
    source
  }
}
