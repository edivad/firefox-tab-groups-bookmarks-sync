package tabgroupsbookmarkssync.spi

import tabgroupsbookmarkssync.model.TabGroup

import scala.concurrent.Future

trait TabGroups {
  def onTabGroupCreate(callback: TabGroup => Unit): Unit
  def onTabGroupUpdate(callback: TabGroup => Unit): Unit
  def onTabGroupRemove(callback: TabGroup => Unit): Unit
  def query(): Future[List[TabGroup]]
  def update(request: TabGroups.UpdateTabGroup): Future[TabGroup]
}

object TabGroups {
  final case class UpdateTabGroup(id: TabGroup.Id, title: Option[String])
}
