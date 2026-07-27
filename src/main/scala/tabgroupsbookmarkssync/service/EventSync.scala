package tabgroupsbookmarkssync.service

import tabgroupsbookmarkssync.log.{Log, Source}
import tabgroupsbookmarkssync.model
import tabgroupsbookmarkssync.model.*
import tabgroupsbookmarkssync.spi.*

import scala.concurrent.{ExecutionContext, Future}

final class EventSync(
  root: BookmarkNode.Id,
  initial: State,
  bookmarks: Bookmarks,
  planner: SyncPlanner,
  tabGroups: TabGroups,
  tabs: Tabs,
)(using ExecutionContext) {

  private var queue: Future[State] = Future.successful(initial)

  def whenIdle(): Future[State] = queue

  def tabGroupCreated(tabGroup: TabGroup): Unit =
    enqueue(
      processTabGroupCreated(_, tabGroup),
      e => s"Cannot process tab group create: $e",
    )

  def tabGroupUpdated(tabGroup: TabGroup): Unit =
    enqueue(
      processTabGroupUpdated(_, tabGroup),
      e => s"Cannot process tab group update: $e",
    )

  def tabGroupRemoved(tabGroup: TabGroup): Unit =
    enqueue(
      processTabGroupRemoved(_, tabGroup),
      e => s"Cannot process tab group remove: $e",
    )

  def tabUpdated(tab: Tab): Unit =
    enqueue(
      processTabUpdated(_, tab),
      e => s"Cannot process tab update: $e",
    )

  private def enqueue(
    doNext: State => Future[State],
    onErrorMessage: Throwable => String,
  ): Unit = {
    val prev = queue
    queue = prev.flatMap(state =>
      doNext(state).recover { e =>
        Log(Source.EventSync, onErrorMessage(e))
        state
      },
    )
  }

  private def processTabGroupCreated(state: State, tabGroup: TabGroup): Future[State] =
    for {
      _ <- Log.future(Source.EventSync, s"Tab Group created: $tabGroup")
      folder <- planner.planFolderCreation(state, tabGroup) match {
        case Left(e) => Future.failed(IllegalStateException(s"Cannot create folder: $e"))
        case Right(Some(request)) => bookmarks.createFolder(request)
        case Right(None) =>
          state.getFolder(tabGroup.id) match {
            case Some(f) => Future.successful(f)
            case None =>
              Future.failed(IllegalStateException(s"Folder not found for tab group ${tabGroup.id}"))
          }
      }
    } yield state.setFolder(tabGroup.id, folder)

  private def processTabGroupUpdated(state: State, tabGroup: TabGroup): Future[State] =
    for {
      _ <- Log.future(Source.EventSync, s"Tab Group updated: $tabGroup")
      folder <- planner.planTabGroupUpdate(state, tabGroup) match {
        case Left(e) => Future.failed(IllegalStateException(s"Cannot update folder: $e"))
        case Right(request) => bookmarks.updateFolder(request)
      }
    } yield state.setFolder(tabGroup.id, folder)

  private def processTabGroupRemoved(state: State, tabGroup: TabGroup): Future[State] =
    for {
      _ <- Log.future(Source.EventSync, s"Tab Group removed: $tabGroup")
      _ <- planner.planFolderRemoval(state, tabGroup) match {
        case Left(e) => Future.failed(IllegalStateException(s"Cannot remove folder: $e"))
        case Right(request) => bookmarks.removeFolder(request)
      }
    } yield state.removeTabGroupState(tabGroup.id).removeFolder(tabGroup.id)

  private def processTabUpdated(state: State, t: Tab): Future[State] =
    for {
      _ <- Log.future(Source.EventSync, s"Tab updated: $t")
      nextState <- (state.getTabGroupForTabId(t.id), t.groupId) match {
        case (None, None) => noChangesToGroups(state)
        case (None, Some(curr)) => tabMovedToNewGroup(state, t, curr)
        case (Some(prev), None) => tabRemovedFromGroup(state, t, prev)
        case (Some(prev), Some(curr)) if prev == curr => noChangesToGroups(state)
        case (Some(prev), Some(curr)) => tabMovedBetweenGroups(state, t, prev, curr)
      }
    } yield nextState

  private def noChangesToGroups(state: State): Future[State] =
    Future.successful(state)

  private def tabMovedToNewGroup(state: State, tab: Tab, curr: TabGroup.Id): Future[State] =
    for {
      _ <- Log.future(Source.EventSync, s"Tab moved to new group $curr")
      created <- planner.planBookmarkCreation(state, tab, curr) match {
        case Left(e) => Future.failed(IllegalStateException(s"Cannot move tab into group: $e"))
        case Right(Some(request)) => bookmarks.createBookmark(request)
        case Right(None) =>
          state.getBookmark(tab.id) match {
            case Some(b) => Future.successful(b)
            case None =>
              Future.failed(IllegalStateException(s"Bookmark not found for tab ${tab.id}"))
          }
      }
    } yield state.mapTab(tab.id, created, curr)

  private def tabRemovedFromGroup(state: State, tab: Tab, prev: TabGroup.Id): Future[State] =
    for {
      _ <- Log.future(Source.EventSync, s"Tab removed from group $prev")
      _ <- planner.planBookmarkRemoval(state, tab) match {
        case Left(e) => Future.failed(IllegalStateException(s"Cannot remove bookmark: $e"))
        case Right(request) =>
          bookmarks.removeBookmark(request).recover { e =>
            Log(Source.EventSync, s"Bookmark (probably) already deleted: $e")
          }
      }
    } yield state.removeBookmark(tab.id).removeTabGroupForTabId(tab.id)

  private def tabMovedBetweenGroups(
    state: State,
    tab: Tab,
    prev: TabGroup.Id,
    curr: TabGroup.Id,
  ): Future[State] =
    for {
      _ <- Log.future(Source.EventSync, s"Tab moved from group $prev to group $curr")
      (remove, create) <- planner.planBookmarkRecreation(state, tab, curr) match {
        case Left(e) => Future.failed(IllegalStateException(s"Cannot recreate bookmark: $e"))
        case Right(requests) => Future.successful(requests)
      }
      _ <- bookmarks.removeBookmark(remove).recover { e =>
        Log(Source.EventSync, s"Bookmark (probably) already deleted: $e")
      }
      created <- bookmarks.createBookmark(create)
    } yield state.mapTab(tab.id, created, curr)

}
