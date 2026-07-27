package util

import tabgroupsbookmarkssync.config.Config
import tabgroupsbookmarkssync.model.{BookmarkNode, State, Tab, TabGroup}

object Fixtures {

  private var nextId = 0L
  private def nextNumericId: Long = { nextId += 1; nextId }

  def title(): Option[String] = Some(s"title ${nextNumericId}")

  def url(): Option[String] = Some(s"https://example.com/${nextNumericId}")

  def folder(
    id: BookmarkNode.Id = BookmarkNode.Id(nextNumericId.toString),
    parentId: BookmarkNode.Id = BookmarkNode.Id(nextNumericId.toString),
    title: Option[String] = title(),
  ): BookmarkNode.Folder = BookmarkNode.Folder(id = id, parentId = parentId, title = title)

  def tabGroup(
    id: TabGroup.Id = TabGroup.Id(nextNumericId.toString),
    title: Option[String] = title(),
  ): TabGroup = TabGroup(id = id, title = title)

  def tab(
    id: Tab.Id = Tab.Id(nextNumericId.toString),
    groupId: Option[TabGroup.Id] = Some(TabGroup.Id(nextNumericId.toString)),
    url: Option[String] = url(),
    title: Option[String] = title(),
  ): Tab = Tab(id = id, groupId = groupId, url = url, title = title)

  def bookmark(
    id: BookmarkNode.Id = BookmarkNode.Id(nextNumericId.toString),
    parentId: BookmarkNode.Id = BookmarkNode.Id(nextNumericId.toString),
    title: Option[String] = title(),
    url: Option[String] = url(),
  ): BookmarkNode.Bookmark =
    BookmarkNode.Bookmark(id = id, parentId = parentId, title = title, url = url)

  def newState(
    foldersByTabGroupId: Map[TabGroup.Id, BookmarkNode.Folder] = Map.empty,
    bookmarksByTabId: Map[Tab.Id, BookmarkNode.Bookmark] = Map.empty,
    tabGroupsForTabs: Map[Tab.Id, TabGroup.Id] = Map.empty,
  ): State = State(foldersByTabGroupId, bookmarksByTabId, tabGroupsForTabs)

  def config(): Config = Config(
    syncFolderName = s"Folder ${nextNumericId}",
    otherBookmarksId = BookmarkNode.Id(nextNumericId.toString),
  )

}
