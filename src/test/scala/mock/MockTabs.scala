package mock

import tabgroupsbookmarkssync.model.{Tab, TabGroup}
import tabgroupsbookmarkssync.spi.Tabs

import scala.collection.mutable
import scala.concurrent.Future

case class MockTabs(
  queryResponses: mutable.ListBuffer[List[Tab]] = mutable.ListBuffer.empty,
  createRequests: mutable.ListBuffer[Tabs.CreateTab] = mutable.ListBuffer.empty,
  createResponses: mutable.ListBuffer[Tab] = mutable.ListBuffer.empty,
  groupRequests: mutable.ListBuffer[Tabs.GroupTabs] = mutable.ListBuffer.empty,
  groupResponses: mutable.ListBuffer[TabGroup.Id] = mutable.ListBuffer.empty,
  updatedCallbacks: mutable.ListBuffer[Tab => Unit] = mutable.ListBuffer.empty,
) extends Tabs {

  def snapshot: MockTabs.Snapshot = MockTabs.Snapshot(
    create = createRequests.toList,
    group = groupRequests.toList,
    listeners = MockTabs.Listeners(updated = updatedCallbacks.size),
  )

  override def onTabUpdate(callback: Tab => Unit): Unit = updatedCallbacks.addOne(callback)

  override def query(): Future[List[Tab]] = Future.successful(queryResponses.remove(0))

  override def create(request: Tabs.CreateTab): Future[Tab] = {
    createRequests.addOne(request)
    Future.successful(createResponses.remove(0))
  }

  override def group(request: Tabs.GroupTabs): Future[TabGroup.Id] = {
    groupRequests.addOne(request)
    Future.successful(groupResponses.remove(0))
  }

  def fireUpdated(tab: Tab): Unit = updatedCallbacks.foreach(_.apply(tab))

}

object MockTabs {

  val unused: MockTabs = MockTabs()

  case class Listeners(updated: Int = 0)

  case class Snapshot(
    create: List[Tabs.CreateTab] = Nil,
    group: List[Tabs.GroupTabs] = Nil,
    listeners: Listeners = Listeners(),
  )

}
