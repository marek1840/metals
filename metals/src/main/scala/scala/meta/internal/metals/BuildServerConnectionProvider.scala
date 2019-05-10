package scala.meta.internal.metals

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

final class BuildServerConnectionProvider(
    buildTools: BuildTools,
    bloopServers: BloopServers,
    bspServers: BspServers
)(implicit ec: ExecutionContext) {

  def openConnection(
      listener: AnyRef
  ): Future[Option[BuildServerConnection]] = {
    if (buildTools.isBloop) bloopServers.newServer(listener)
    else bspServers.newServer(listener)
  }
}
