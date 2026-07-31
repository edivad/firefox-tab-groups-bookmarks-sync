package mock

import tabgroupsbookmarkssync.model.*
import tabgroupsbookmarkssync.service.{SyncError, SyncPlanner}
import tabgroupsbookmarkssync.spi.{Bookmarks, Tabs}

import scala.collection.mutable

case class MockSyncPlanner(
  createFolderResponses: mutable.ListBuffer[
    Either[SyncError, Option[Bookmarks.CreateFolder]],
  ] = mutable.ListBuffer.empty,
  updateFolderResponses: mutable.ListBuffer[
    Either[SyncError, Bookmarks.UpdateFolder],
  ] = mutable.ListBuffer.empty,
  removeFolderResponses: mutable.ListBuffer[
    Either[SyncError, Bookmarks.RemoveFolder],
  ] = mutable.ListBuffer.empty,
  createBookmarkResponses: mutable.ListBuffer[
    Either[SyncError, Option[Bookmarks.CreateBookmark]],
  ] = mutable.ListBuffer.empty,
  removeBookmarkResponses: mutable.ListBuffer[
    Either[SyncError, Bookmarks.RemoveBookmark],
  ] = mutable.ListBuffer.empty,
  recreateBookmarkResponses: mutable.ListBuffer[
    Either[SyncError, (Bookmarks.RemoveBookmark, Bookmarks.CreateBookmark)],
  ] = mutable.ListBuffer.empty,
  planTabGroupCreationResponses: mutable.ListBuffer[
    Either[SyncError, Option[TabGroup.Id]],
  ] = mutable.ListBuffer.empty,
  planTabCreationResponses: mutable.ListBuffer[
    Either[SyncError, Option[Tabs.CreateTab]],
  ] = mutable.ListBuffer.empty,
) extends SyncPlanner {

  override def planFolderCreation(
    state: State,
    tabGroup: TabGroup,
  ): Either[SyncError, Option[Bookmarks.CreateFolder]] =
    createFolderResponses.remove(0)

  override def planTabGroupUpdate(
    state: State,
    tabGroup: TabGroup,
  ): Either[SyncError, Bookmarks.UpdateFolder] =
    updateFolderResponses.remove(0)

  override def planFolderRemoval(
    state: State,
    tabGroup: TabGroup,
  ): Either[SyncError, Bookmarks.RemoveFolder] =
    removeFolderResponses.remove(0)

  override def planBookmarkCreation(
    state: State,
    tab: Tab,
    tabGroupId: TabGroup.Id,
  ): Either[SyncError, Option[Bookmarks.CreateBookmark]] =
    createBookmarkResponses.remove(0)

  override def planBookmarkRemoval(
    state: State,
    tab: Tab,
  ): Either[SyncError, Bookmarks.RemoveBookmark] =
    removeBookmarkResponses.remove(0)

  override def planBookmarkRecreation(
    state: State,
    tab: Tab,
    tabGroupId: TabGroup.Id,
  ): Either[SyncError, (Bookmarks.RemoveBookmark, Bookmarks.CreateBookmark)] =
    recreateBookmarkResponses.remove(0)

  override def planTabGroupCreation(
    folder: BookmarkNode.Folder,
    tabGroups: List[TabGroup],
  ): Either[SyncError, Option[TabGroup.Id]] =
    planTabGroupCreationResponses.remove(0)

  override def planTabCreation(
    bookmark: BookmarkNode.Bookmark,
    tabs: List[Tab],
    tabGroupId: Option[TabGroup.Id],
  ): Either[SyncError, Option[Tabs.CreateTab]] =
    planTabCreationResponses.remove(0)

}

object MockSyncPlanner {
  val unused: MockSyncPlanner = MockSyncPlanner()
}
