package tabgroupsbookmarkssync.spi.browser

import scala.scalajs.js

@js.native
private[browser] trait BookmarkTreeNode extends js.Object {
  val id: String = js.native
  val parentId: String = js.native
  val title: String = js.native
  val `type`: String = js.native
  val url: js.UndefOr[String] = js.native
  val children: js.UndefOr[js.Array[BookmarkTreeNode]] = js.native
}

@js.native
@js.annotation.JSGlobal("browser.bookmarks")
private[browser] object BookmarksFacade extends js.Object {
  def get(id: String): js.Promise[js.Array[BookmarkTreeNode]] = js.native
  def getChildren(id: String): js.Promise[js.Array[BookmarkTreeNode]] = js.native
  def create(details: js.Object): js.Promise[BookmarkTreeNode] = js.native
  def update(id: String, change: js.Object): js.Promise[BookmarkTreeNode] = js.native
  def removeTree(id: String): js.Promise[Unit] = js.native
  def remove(id: String): js.Promise[Unit] = js.native
}

@js.native
private[browser] trait TabGroup extends js.Object {
  val id: Double = js.native
  val title: String = js.native
  val color: String = js.native
  val windowId: Double = js.native
  val collapsed: Boolean = js.native
}

@js.native
private[browser] trait TabGroupsOnUpdated extends js.Object {
  def addListener(callback: js.Function1[TabGroup, Unit]): Unit = js.native
}

@js.native
@js.annotation.JSGlobal("browser.tabGroups")
private[browser] object TabGroupsFacade extends js.Object {
  val onCreated: TabGroupsOnUpdated = js.native
  val onUpdated: TabGroupsOnUpdated = js.native
  val onRemoved: TabGroupsOnUpdated = js.native
  def query(queryInfo: js.Object): js.Promise[js.Array[TabGroup]] = js.native
  def update(groupId: Double, updateProperties: js.Object): js.Promise[TabGroup] = js.native
}

@js.native
private[browser] trait Tab extends js.Object {
  val id: js.UndefOr[Double] = js.native
  val groupId: js.UndefOr[Double] = js.native
  val url: js.UndefOr[String] = js.native
  val title: js.UndefOr[String] = js.native
}

@js.native
private[browser] trait TabsOnUpdated extends js.Object {
  def addListener(callback: js.Function3[Double, js.Object, Tab, Unit]): Unit = js.native
}

@js.native
@js.annotation.JSGlobal("browser.tabs")
private[browser] object TabsFacade extends js.Object {
  val onUpdated: TabsOnUpdated = js.native
  def query(queryInfo: js.Object): js.Promise[js.Array[Tab]] = js.native
  def create(createProperties: js.Object): js.Promise[Tab] = js.native
  def group(options: js.Object): js.Promise[Double] = js.native
}
