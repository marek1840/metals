package scala.meta.internal.metals.debug

import com.google.gson.JsonElement
import org.eclipse.lsp4j.debug.DisconnectArguments
import org.eclipse.lsp4j.debug.InitializeRequestArguments
import org.eclipse.lsp4j.debug.OutputEventArguments
import org.eclipse.lsp4j.debug.SetBreakpointsArguments
import org.eclipse.lsp4j.debug.SetBreakpointsResponse
import org.eclipse.lsp4j.debug.Source
import org.eclipse.lsp4j.debug.SourceArguments
import org.eclipse.lsp4j.debug.SourceResponse
import org.eclipse.lsp4j.jsonrpc.debug.messages.DebugRequestMessage
import org.eclipse.lsp4j.jsonrpc.debug.messages.DebugResponseMessage
import org.eclipse.lsp4j.jsonrpc.messages.NotificationMessage
import org.eclipse.lsp4j.jsonrpc.messages.RequestMessage
import org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage
import org.eclipse.lsp4j.{debug => dap}
import scala.reflect.ClassTag
import scala.util.Failure
import scala.util.Success
import scala.util.Try

private[debug] object DebugProtocol {
  import scala.meta.internal.metals.JsonParser._

  object SetBreakpointRequest {
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

  object SetBreakpointResponse {
    def unapply(
        request: DebugResponseMessage
    ): Option[SetBreakpointsResponse] = {
      if (request.getMethod != "setBreakpoints") None
      else if (request.getError != null) None
      else {
        request.getResult match {
          case json: JsonElement =>
            json.as[SetBreakpointsResponse].toOption
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

  object SourceRequest {
    def unapply(request: RequestMessage): Option[SourceArguments] = {
      if (request.getMethod != "source") None
      else {
        request.getParams match {
          case json: JsonElement => json.as[SourceArguments].toOption
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

  object StackTraceResponse {
    def unapply(
        response: DebugResponseMessage
    ): Option[dap.StackTraceResponse] = {
      if (response.getMethod != "stackTrace") None
      else {
        response.getResult match {
          case json: JsonElement => json.as[dap.StackTraceResponse].toOption
          case _ => None
        }
      }
    }
  }

  object OutputNotification {
    def unapply(
        notification: NotificationMessage
    ): Option[OutputEventArguments] = {
      if (notification.getMethod != "output") None
      else {
        notification.getParams match {
          case json: JsonElement => json.as[OutputEventArguments].toOption
          case _ => None
        }
      }
    }
  }

  def toRequest(args: SetBreakpointsArguments): RequestMessage = {
    val request = new DebugRequestMessage
    request.setMethod("setBreakpoints")
    request.setParams(args.toJson)
    request
  }

  def toResponse(id: String, args: SetBreakpointsResponse): ResponseMessage = {
    val response = new DebugResponseMessage
    response.setId(id)
    response.setMethod("setBreakpoints")
    response.setResult(args.toJson)
    response
  }

  def toResponse(id: String, args: SourceResponse): ResponseMessage = {
    val response = new DebugResponseMessage
    response.setId(id)
    response.setMethod("source")
    response.setResult(args.toJson)
    response
  }

  def parseResponse[A: ClassTag](response: ResponseMessage): Try[A] = {
    val result = response.getResult
    result match {
      case json: JsonElement =>
        json.as[A]
      case _ =>
        Failure(new IllegalStateException(s"$result is not a json"))
    }
  }

  def responseTo(request: RequestMessage): ResponseMessage = {
    val response = new DebugResponseMessage
    response.setMethod(request.getMethod)
    response.setId(request.getId)
    response.setResult(().toJson)
    response
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
