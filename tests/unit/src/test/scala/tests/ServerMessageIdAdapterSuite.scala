package tests

import org.eclipse.lsp4j.jsonrpc.MessageConsumer
import org.eclipse.lsp4j.jsonrpc.debug.messages.DebugRequestMessage
import org.eclipse.lsp4j.jsonrpc.debug.messages.DebugResponseMessage
import org.eclipse.lsp4j.jsonrpc.messages.IdentifiableMessage
import org.eclipse.lsp4j.jsonrpc.messages.Message
import scala.collection.mutable
import scala.meta.internal.metals.debug.RemoteEndpoint
import scala.meta.internal.metals.debug.ServerMessageIdAdapter

//object ServerMessageIdAdapterSuite extends BaseSuite {
//  test("fill-empty-id") {
//    implicit val adapter: ServerMessageAdapter = new ServerMessageAdapter
//
//    assertRequest(id = None)(
//      serverSiteId = Some(0),
//      clientSiteId = None
//    )
//  }
//
//  test("keep-original-id") {
//    implicit val adapter: ServerMessageAdapter = new ServerMessageAdapter
//
//    assertRequest(id = Some(0))(
//      serverSiteId = Some(0),
//      clientSiteId = Some(0)
//    )
//  }
//
//  test("keep-client-site-sequence") {
//    implicit val adapter: ServerMessageAdapter = new ServerMessageAdapter
//
//    assertRequest(id = None)(
//      serverSiteId = Some(0),
//      clientSiteId = None
//    )
//
//    assertRequest(id = Some(0))(
//      serverSiteId = Some(1),
//      clientSiteId = Some(0)
//    )
//
//    assertRequest(id = None)(
//      serverSiteId = Some(2),
//      clientSiteId = None
//    )
//
//    assertRequest(id = None)(
//      serverSiteId = Some(3),
//      clientSiteId = None
//    )
//
//    assertRequest(id = Some(1))(
//      serverSiteId = Some(4),
//      clientSiteId = Some(1)
//    )
//  }
//
//  case class Sequence(
//      serverSiteId: Option[String],
//      clientSiteId: Option[String]
//  )
//
//  private def assertRequest(id: Option[Int])(
//      serverSiteId: Option[Int],
//      clientSiteId: Option[Int]
//  ): Unit = {
//    val remote = new RemoteEndpoint {
//      private var id = 0
//      private var listener: MessageConsumer = _
//      override def cancel(): Unit = ()
//      override def consume(message: Message): Unit = {
//        message match {
//          case message: DebugRequestMessage =>
//            validateId(message)
//          case message: DebugResponseMessage =>
//            validateId(message)
//          case _ =>
//          // ignore
//        }
//      }
//      private def validateId(message: IdentifiableMessage): Unit = {
//        assertEquals(message.getId, id)
//        id += 1
//      }
//
//      override def listen(consumer: MessageConsumer): Unit = {
//        assert(listener == null)
//        listener = consumer
//      }
//    }
//    val adapter = new ServerMessageIdAdapter
//    val request = new DebugRequestMessage
//    id.foreach(request.setId)
//
//    adapter.adaptMessageFromClient(request)
//
//    val response = new DebugResponseMessage
//    response.setId(request.getId.toInt)
//
//    adapter.adaptMessageFromServer(response)
//
//    val expectedClientSiteId = clientSiteId.getOrElse("None").toString
//    val expectedServerSiteId = serverSiteId.getOrElse("None").toString
//
//    val actualClientSiteId = Option(response.getId).getOrElse("None")
//    val actualServerSiteId = Option(request.getId).getOrElse("None")
//
//    val errors = mutable.Buffer.empty[String]
//    if (expectedClientSiteId != actualClientSiteId) {
//      errors += s"Invalid client-site id: Expected: $expectedClientSiteId, actual: $actualClientSiteId"
//    }
//
//    if (expectedServerSiteId != actualServerSiteId) {
//      errors += s"Invalid server-site id: Expected: $expectedClientSiteId, actual: $actualClientSiteId"
//    }
//
//    if (errors.nonEmpty) {
//      throw new TestFailedException(errors.mkString("\n"))
//    }
//  }
//}
