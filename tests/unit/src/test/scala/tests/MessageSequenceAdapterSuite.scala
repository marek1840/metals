package tests

import org.eclipse.lsp4j.jsonrpc.debug.messages.DebugRequestMessage
import org.eclipse.lsp4j.jsonrpc.debug.messages.DebugResponseMessage
import scala.collection.mutable
import scala.meta.internal.metals.debug.MessageIdAdapter

object MessageSequenceAdapterSuite extends BaseSuite {
  test("fill-empty-id") {
    implicit val adapter: MessageIdAdapter = new MessageIdAdapter

    assertRequest(id = None)(
      serverSiteId = Some(0),
      clientSiteId = None
    )
  }

  test("keep-original-id") {
    implicit val adapter: MessageIdAdapter = new MessageIdAdapter

    assertRequest(id = Some(0))(
      serverSiteId = Some(0),
      clientSiteId = Some(0)
    )
  }

  test("keep-client-site-sequence") {
    implicit val adapter: MessageIdAdapter = new MessageIdAdapter

    assertRequest(id = None)(
      serverSiteId = Some(0),
      clientSiteId = None
    )

    assertRequest(id = Some(0))(
      serverSiteId = Some(1),
      clientSiteId = Some(0)
    )

    assertRequest(id = None)(
      serverSiteId = Some(2),
      clientSiteId = None
    )

    assertRequest(id = None)(
      serverSiteId = Some(3),
      clientSiteId = None
    )

    assertRequest(id = Some(1))(
      serverSiteId = Some(4),
      clientSiteId = Some(1)
    )
  }

  case class Sequence(
      serverSiteId: Option[String],
      clientSiteId: Option[String]
  )

  private def assertRequest(id: Option[Int])(
      serverSiteId: Option[Int],
      clientSiteId: Option[Int]
  )(implicit adapter: MessageIdAdapter): Unit = {
    val request = new DebugRequestMessage
    id.foreach(request.setId)

    adapter.adaptMessageFromClient(request)

    val response = new DebugResponseMessage
    response.setId(request.getId.toInt)

    adapter.adaptMessageFromServer(response)

    val expectedClientSiteId = clientSiteId.getOrElse("None").toString
    val expectedServerSiteId = serverSiteId.getOrElse("None").toString

    val actualClientSiteId = Option(response.getId).getOrElse("None")
    val actualServerSiteId = Option(request.getId).getOrElse("None")

    val errors = mutable.Buffer.empty[String]
    if (expectedClientSiteId != actualClientSiteId) {
      errors += s"Invalid client-site id: Expected: $expectedClientSiteId, actual: $actualClientSiteId"
    }

    if (expectedServerSiteId != actualServerSiteId) {
      errors += s"Invalid server-site id: Expected: $expectedClientSiteId, actual: $actualClientSiteId"
    }

    if (errors.nonEmpty) {
      throw new TestFailedException(errors.mkString("\n"))
    }
  }
}
