package mock

import tabgroupsbookmarkssync.model.TabGroup
import tabgroupsbookmarkssync.spi.TabGroups

import scala.collection.mutable
import scala.concurrent.Future

case class MockTabGroups(
  queryResponses: mutable.ListBuffer[List[TabGroup]] = mutable.ListBuffer.empty,
  updateRequests: mutable.ListBuffer[TabGroups.UpdateTabGroup] = mutable.ListBuffer.empty,
  updateResponses: mutable.ListBuffer[TabGroup] = mutable.ListBuffer.empty,
  createdCallbacks: mutable.ListBuffer[TabGroup => Unit] = mutable.ListBuffer.empty,
  updatedCallbacks: mutable.ListBuffer[TabGroup => Unit] = mutable.ListBuffer.empty,
  removedCallbacks: mutable.ListBuffer[TabGroup => Unit] = mutable.ListBuffer.empty,
) extends TabGroups {

  def snapshot: MockTabGroups.Snapshot = MockTabGroups.Snapshot(
    update = updateRequests.toList,
    listeners = MockTabGroups.Listeners(
      created = createdCallbacks.size,
      updated = updatedCallbacks.size,
      removed = removedCallbacks.size,
    ),
  )

  override def onTabGroupCreate(callback: TabGroup => Unit): Unit =
    createdCallbacks.addOne(callback)

  override def onTabGroupUpdate(callback: TabGroup => Unit): Unit =
    updatedCallbacks.addOne(callback)

  override def onTabGroupRemove(callback: TabGroup => Unit): Unit =
    removedCallbacks.addOne(callback)

  override def query(): Future[List[TabGroup]] = Future.successful(queryResponses.remove(0))

  override def update(request: TabGroups.UpdateTabGroup): Future[TabGroup] = {
    updateRequests.addOne(request)
    Future.successful(updateResponses.remove(0))
  }

  def fireCreated(tabGroup: TabGroup): Unit = createdCallbacks.foreach(_.apply(tabGroup))

  def fireUpdated(tabGroup: TabGroup): Unit = updatedCallbacks.foreach(_.apply(tabGroup))

  def fireRemoved(tabGroup: TabGroup): Unit = removedCallbacks.foreach(_.apply(tabGroup))

}

object MockTabGroups {

  val unused: MockTabGroups = MockTabGroups()

  case class Listeners(created: Int = 0, updated: Int = 0, removed: Int = 0)

  case class Snapshot(
    update: List[TabGroups.UpdateTabGroup] = Nil,
    listeners: Listeners = Listeners(),
  )

}
