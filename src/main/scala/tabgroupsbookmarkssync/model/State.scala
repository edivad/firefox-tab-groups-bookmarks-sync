package tabgroupsbookmarkssync.model

case class State(
  foldersByTabGroupId: Map[TabGroup.Id, BookmarkNode.Folder],
  bookmarksByTabId: Map[Tab.Id, BookmarkNode.Bookmark],
  tabGroupsForTabs: Map[Tab.Id, TabGroup.Id],
) {

  def getFolder(id: TabGroup.Id): Option[BookmarkNode.Folder] =
    foldersByTabGroupId.get(id)

  def setFolder(id: TabGroup.Id, folder: BookmarkNode.Folder): State =
    copy(foldersByTabGroupId = foldersByTabGroupId + (id -> folder))

  def removeFolder(id: TabGroup.Id): State =
    copy(foldersByTabGroupId = foldersByTabGroupId - id)

  def getTabGroupForTabId(id: Tab.Id): Option[TabGroup.Id] =
    tabGroupsForTabs.get(id)

  def setTabGroupForTabId(id: Tab.Id, tabGroupId: TabGroup.Id): State =
    copy(tabGroupsForTabs = tabGroupsForTabs + (id -> tabGroupId))

  def removeTabGroupForTabId(id: Tab.Id): State =
    copy(tabGroupsForTabs = tabGroupsForTabs - id)

  def removeTabGroupState(id: TabGroup.Id): State = {
    val tabIds = tabGroupsForTabs.collect { case (tabId, groupId) if groupId == id => tabId }
    copy(
      tabGroupsForTabs = tabGroupsForTabs -- tabIds,
      bookmarksByTabId = bookmarksByTabId -- tabIds,
    )
  }

  def getBookmark(id: Tab.Id): Option[BookmarkNode.Bookmark] =
    bookmarksByTabId.get(id)

  def setBookmark(id: Tab.Id, bookmark: BookmarkNode.Bookmark): State =
    copy(bookmarksByTabId = bookmarksByTabId + (id -> bookmark))

  def removeBookmark(id: Tab.Id): State =
    copy(bookmarksByTabId = bookmarksByTabId - id)

  def mapTab(id: Tab.Id, bookmark: BookmarkNode.Bookmark, tabGroupId: TabGroup.Id): State =
    copy(
      bookmarksByTabId = bookmarksByTabId + (id -> bookmark),
      tabGroupsForTabs = tabGroupsForTabs + (id -> tabGroupId),
    )

}

object State {
  def empty: State = State(Map.empty, Map.empty, Map.empty)
}
