package mock

import tabgroupsbookmarkssync.model.TabGroup
import tabgroupsbookmarkssync.spi.TabGroups

import scala.collection.mutable
import scala.concurrent.Future

case class MockTabGroups(
  queryResponses: mutable.ListBuffer[List[TabGroup]] = mutable.ListBuffer.empty,
  updateRequests: mutable.ListBuffer[TabGroups.UpdateTabGroup] = mutable.ListBuffer.empty,
  updateResponses: mutable.ListBuffer[TabGroup] = mutable.ListBuffer.empty,
) extends TabGroups {

  def snapshot: MockTabGroups.Snapshot = MockTabGroups.Snapshot(
    update = updateRequests.toList,
  )

  override def onTabGroupCreate(callback: TabGroup => Unit): Unit = ()

  override def onTabGroupUpdate(callback: TabGroup => Unit): Unit = ()

  override def onTabGroupRemove(callback: TabGroup => Unit): Unit = ()

  override def query(): Future[List[TabGroup]] = Future.successful(queryResponses.remove(0))

  override def update(request: TabGroups.UpdateTabGroup): Future[TabGroup] = {
    updateRequests.addOne(request)
    Future.successful(updateResponses.remove(0))
  }

}

object MockTabGroups {
  val unused: MockTabGroups = MockTabGroups()

  case class Snapshot(
    update: List[TabGroups.UpdateTabGroup] = Nil,
  )

}
