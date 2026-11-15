package mock

import tabgroupsbookmarkssync.model.SessionState
import tabgroupsbookmarkssync.spi.SessionStore

import scala.collection.mutable
import scala.concurrent.Future

case class MockSessionStore(
  initial: Option[SessionState] = None,
  loadFailures: mutable.ListBuffer[Throwable] = mutable.ListBuffer.empty,
  saveFailures: mutable.ListBuffer[Throwable] = mutable.ListBuffer.empty,
  clearFailures: mutable.ListBuffer[Throwable] = mutable.ListBuffer.empty,
  loadRequests: mutable.ListBuffer[Unit] = mutable.ListBuffer.empty,
  saveRequests: mutable.ListBuffer[SessionState] = mutable.ListBuffer.empty,
  clearRequests: mutable.ListBuffer[Unit] = mutable.ListBuffer.empty,
) extends SessionStore {

  def snapshot: MockSessionStore.Snapshot = MockSessionStore.Snapshot(
    load = loadRequests.toList,
    save = saveRequests.toList,
    clear = clearRequests.toList,
  )

  override def load: Future[Option[SessionState]] = {
    loadRequests.addOne(())
    if loadFailures.nonEmpty then Future.failed(loadFailures.remove(0))
    else Future.successful(initial)
  }

  override def save(session: SessionState): Future[Unit] = {
    saveRequests.addOne(session)
    if saveFailures.nonEmpty then Future.failed(saveFailures.remove(0))
    else Future.successful(())
  }

  override def clear(): Future[Unit] = {
    clearRequests.addOne(())
    if clearFailures.nonEmpty then Future.failed(clearFailures.remove(0))
    else Future.successful(())
  }

}

object MockSessionStore {

  val unused: MockSessionStore = MockSessionStore()

  case class Snapshot(
    load: List[Unit] = Nil,
    save: List[SessionState] = Nil,
    clear: List[Unit] = Nil,
  )

}
