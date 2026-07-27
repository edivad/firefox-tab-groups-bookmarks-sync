package tabgroupsbookmarkssync.service

import tabgroupsbookmarkssync.model
import tabgroupsbookmarkssync.model.{BookmarkNode, State, Tab, TabGroup}
import tabgroupsbookmarkssync.spi.{Bookmarks, Tabs}

trait SyncPlanner {

  def planFolderCreation(
    state: State,
    tabGroup: TabGroup,
  ): Either[SyncError, Option[Bookmarks.CreateFolder]]

  def planTabGroupUpdate(
    state: State,
    tabGroup: TabGroup,
  ): Either[SyncError, Bookmarks.UpdateFolder]

  def planFolderRemoval(
    state: State,
    tabGroup: TabGroup,
  ): Either[SyncError, Bookmarks.RemoveFolder]

  def planBookmarkCreation(
    state: State,
    tab: Tab,
    tabGroupId: TabGroup.Id,
  ): Either[SyncError, Option[Bookmarks.CreateBookmark]]

  def planBookmarkRemoval(
    state: State,
    tab: Tab,
  ): Either[SyncError, Bookmarks.RemoveBookmark]

  def planBookmarkRecreation(
    state: State,
    tab: Tab,
    tabGroupId: TabGroup.Id,
  ): Either[SyncError, (Bookmarks.RemoveBookmark, Bookmarks.CreateBookmark)]

  def planTabGroupCreation(
    folder: BookmarkNode.Folder,
    tabGroups: List[TabGroup],
  ): Either[SyncError, Option[TabGroup.Id]]

  def planTabCreation(
    bookmark: BookmarkNode.Bookmark,
    tabs: List[Tab],
    tabGroupId: Option[TabGroup.Id],
  ): Either[SyncError, Option[Tabs.CreateTab]]

}

object SyncPlanner {

  def apply(root: BookmarkNode.Id): SyncPlanner = new SyncPlanner {

    override def planFolderCreation(
      state: State,
      tabGroup: TabGroup,
    ): Either[SyncError, Option[Bookmarks.CreateFolder]] =
      state.getFolder(tabGroup.id) match {
        case None => Right(Some(Bookmarks.CreateFolder(parentId = root, title = tabGroup.title)))
        case Some(f) => Right(None)
      }

    override def planTabGroupUpdate(
      state: State,
      tabGroup: TabGroup,
    ): Either[SyncError, Bookmarks.UpdateFolder] =
      state.getFolder(tabGroup.id) match {
        case None => Left(SyncError.FolderNotFoundForTabGroup(tabGroup))
        case Some(f) => Right(Bookmarks.UpdateFolder(id = f.id, title = tabGroup.title))
      }

    override def planFolderRemoval(
      state: State,
      tabGroup: TabGroup,
    ): Either[SyncError, Bookmarks.RemoveFolder] =
      state.getFolder(tabGroup.id) match {
        case None => Left(SyncError.FolderNotFoundForTabGroup(tabGroup))
        case Some(f) => Right(Bookmarks.RemoveFolder(id = f.id))
      }

    override def planBookmarkCreation(
      state: State,
      tab: Tab,
      tabGroupId: TabGroup.Id,
    ): Either[SyncError, Option[Bookmarks.CreateBookmark]] =
      state.getBookmark(tab.id) match {
        case Some(_) if state.getTabGroupForTabId(tab.id).contains(tabGroupId) => Right(None)
        case _ =>
          for {
            folder <- state
              .getFolder(tabGroupId)
              .toRight(SyncError.FolderNotFoundForTabGroupId(tabGroupId))
            url <- tab.url.toRight(SyncError.MissingUrlForTab(tab))
          } yield Some(Bookmarks.CreateBookmark(parentId = folder.id, title = tab.title, url = url))
      }

    override def planBookmarkRemoval(
      state: State,
      tab: Tab,
    ): Either[SyncError, Bookmarks.RemoveBookmark] =
      state.getBookmark(tab.id) match {
        case None => Left(SyncError.BookmarkNotFoundForTab(tab))
        case Some(b) => Right(Bookmarks.RemoveBookmark(b.id))
      }

    override def planBookmarkRecreation(
      state: State,
      tab: Tab,
      tabGroupId: TabGroup.Id,
    ): Either[SyncError, (Bookmarks.RemoveBookmark, Bookmarks.CreateBookmark)] =
      planBookmarkCreation(state, tab, tabGroupId).flatMap {
        case None => Left(SyncError.MissingUrlForTab(tab))
        case Some(create) => planBookmarkRemoval(state, tab).map(remove => (remove, create))
      }

    override def planTabGroupCreation(
      folder: BookmarkNode.Folder,
      tabGroups: List[TabGroup],
    ): Either[SyncError, Option[TabGroup.Id]] =
      tabGroups.filter(_.title == folder.title) match {
        case matched :: Nil => Right(Some(matched.id))
        case _ => Right(None)
      }

    override def planTabCreation(
      bookmark: BookmarkNode.Bookmark,
      tabs: List[Tab],
      tabGroupId: Option[TabGroup.Id],
    ): Either[SyncError, Option[Tabs.CreateTab]] =
      bookmark.url match {
        case None => Right(None)
        case Some(url) if tabs.exists(_.url.contains(url)) => Right(None)
        case Some(url) => Right(Some(Tabs.CreateTab(url = url, groupId = tabGroupId)))
      }
  }

}
