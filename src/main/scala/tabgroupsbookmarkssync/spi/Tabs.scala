package tabgroupsbookmarkssync.spi

import tabgroupsbookmarkssync.model.{Tab, TabGroup}

import scala.concurrent.Future

trait Tabs {
  def onTabUpdate(callback: Tab => Unit): Unit
  def query(): Future[List[Tab]]
  def create(request: Tabs.CreateTab): Future[Tab]
  def group(request: Tabs.GroupTabs): Future[TabGroup.Id]
}

object Tabs {
  final case class CreateTab(url: String, groupId: Option[TabGroup.Id])
  final case class GroupTabs(ids: List[Tab.Id], groupId: Option[TabGroup.Id])
}
