package scala.meta.internal.metals.debug
import org.eclipse.lsp4j.jsonrpc.messages.Message
import org.eclipse.lsp4j.jsonrpc.messages.RequestMessage
import org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage
import scala.collection.concurrent.TrieMap
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.meta.internal.metals.Cancelable

final class ServerAdapter(server: RemoteEndpoint)(implicit ec: ExecutionContext)
    extends Cancelable {
  private val idAdapter = new MessageIdAdapter
  private val partitions = TrieMap.empty[String, ResponseMessage => Unit]

  def onServerMessage(consumer: Message => Unit): Unit = {
    server.listen {
      case response: ResponseMessage if partitions.contains(response.getId) =>
        partitions
          .remove(response.getId)
          .foreach(callback => callback(response))
      // doesn't use the consumer. Partitioned messages are handled as a future
      case message =>
        idAdapter.adaptMessageFromServer(message)
        consumer(message)
    }
  }

  def send(message: Message): Unit = {
    idAdapter.adaptMessageFromClient(message)
    server.consume(message)
  }

  def sendPartitioned[Response](
      parts: Iterable[RequestMessage]
  ): Future[Iterable[ResponseMessage]] = {
    val responses = parts.map { request =>
      idAdapter.adaptMessageFromClient(request) // fills the id if missing

      val promise = Promise[ResponseMessage]
      partitions += (request.getId -> promise.success)

      server.consume(request)
      promise.future
    }

    Future.sequence(responses)
  }

  override def cancel(): Unit = {
    server.cancel()
  }
}
