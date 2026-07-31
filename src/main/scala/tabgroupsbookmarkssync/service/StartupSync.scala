package tabgroupsbookmarkssync.service

import tabgroupsbookmarkssync.log.{Log, Source}
import tabgroupsbookmarkssync.model
import tabgroupsbookmarkssync.model.*
import tabgroupsbookmarkssync.spi.*

import scala.concurrent.{ExecutionContext, Future}

final class StartupSync(
  root: BookmarkNode.Id,
  bookmarks: Bookmarks,
  planner: SyncPlanner,
  tabGroups: TabGroups,
  tabs: Tabs,
)(using ExecutionContext) {
  import StartupSync.{FolderWithBookmarks, GroupedTabs}

  def synchronize(): Future[State] =
    for {
      existingTabGroups <- tabGroups.query()
      _ <- Log.future(
        Source.StartupSync,
        s"Found ${existingTabGroups.size} tab groups: ${existingTabGroups.map(_.title)}",
      )
      existingTabs <- tabs.query().map(_.filter(_.groupId.isDefined))
      _ <- Log.future(
        Source.StartupSync,
        s"Found ${existingTabs.size} tabs with groups",
      )
      folders <- bookmarks.getChildren(Bookmarks.GetChildren(root)).map { children =>
        children.collect { case f: BookmarkNode.Folder => f }
      }
      _ <- Log.future(
        Source.StartupSync,
        s"Found ${folders.size} folders in root: ${folders.map(_.title)}",
      )
      allGroupedTabs <- Future.traverse(existingTabGroups) { tg =>
        Future.successful(GroupedTabs(tg, existingTabs.filter(_.groupId.contains(tg.id))))
      }
      allFolderWithBookmarks <- Future.traverse(folders) { f =>
        bookmarks.getChildren(Bookmarks.GetChildren(f.id)).map { children =>
          FolderWithBookmarks(f, children.collect { case b: BookmarkNode.Bookmark => b })
        }
      }
      seed = createSeedState(allGroupedTabs, allFolderWithBookmarks)
      updated <- createTabGroupsAndTabs(seed, allGroupedTabs, allFolderWithBookmarks)
      result <- createFoldersAndBookmarks(updated, allGroupedTabs)
    } yield result

  private def createSeedState(
    allGroupedTabs: List[GroupedTabs],
    allFolderWithBookmarks: List[FolderWithBookmarks],
  ) =
    allFolderWithBookmarks.foldLeft(State.empty) {
      case (state, FolderWithBookmarks(folder, bookmarks)) =>
        allGroupedTabs.filter(_.tabGroup.title == folder.title) match {
          case GroupedTabs(tabGroup, tabs) :: Nil =>
            val withFolder = state.setFolder(tabGroup.id, folder)
            bookmarks.foldLeft(withFolder) { (s, bm) =>
              bm.url match {
                case Some(url) =>
                  tabs.find(_.url.contains(url)) match {
                    case Some(tab) => s.mapTab(tab.id, bm, tabGroup.id)
                    case None => s
                  }
                case None => s
              }
            }
          case _ => state
        }
    }

  private def createTabGroupsAndTabs(
    state: State,
    allGroupedTabs: List[GroupedTabs],
    allFolderWithBookmarks: List[FolderWithBookmarks],
  ) = allFolderWithBookmarks.foldLeft(Future.successful(state)) { (state, folderWithBookmarks) =>
    state.flatMap { s =>
      val tabGroups = allGroupedTabs.map(_.tabGroup)
      val tabs = allGroupedTabs.flatMap(_.tabs)
      planner.planTabGroupCreation(folderWithBookmarks.folder, tabGroups) match {
        case Left(e) => Future.failed(IllegalStateException(s"Cannot create tab group: $e"))
        case Right(None) =>
          Log
            .future(
              Source.StartupSync,
              s"Creating new group from folder: ${folderWithBookmarks.folder.title}",
            )
            .flatMap(_ => processTabGroupAndTabsCreation(s, folderWithBookmarks))
        case Right(Some(id)) =>
          Log
            .future(
              Source.StartupSync,
              s"Adding tabs to existing group $id from folder: ${folderWithBookmarks.folder.title}",
            )
            .flatMap(_ => processTabsCreation(s, folderWithBookmarks, id, tabs))
      }
    }
  }

  private def processTabGroupAndTabsCreation(
    state: State,
    folderWithBookmarks: FolderWithBookmarks,
  ) = {
    val FolderWithBookmarks(folder, bookmarks) = folderWithBookmarks
    for {
      createdTabs <- createTabs(bookmarks, List.empty, None)
      tabIds = createdTabs.map { case (tab, _) => tab.id }
      groupId <- tabs.group(Tabs.GroupTabs(tabIds, None))
      updatedTabGroup <- tabGroups.update(
        TabGroups.UpdateTabGroup(groupId, folder.title),
      )
      _ <- Log.future(
        Source.StartupSync,
        s"Created ${createdTabs.size} tab(s) in new group $groupId",
      )
    } yield createdTabs
      .foldLeft(state) { case (acc, (t, b)) =>
        acc.mapTab(t.id, b, updatedTabGroup.id)
      }
      .setFolder(updatedTabGroup.id, folder)
  }

  private def processTabsCreation(
    state: State,
    folderWithBookmarks: FolderWithBookmarks,
    tabGroupId: TabGroup.Id,
    existingTabs: List[Tab],
  ) = {
    val FolderWithBookmarks(folder, bookmarks) = folderWithBookmarks
    for {
      createdTabs <- createTabs(bookmarks, existingTabs, Some(tabGroupId))
      _ <- Log.future(
        Source.StartupSync,
        s"Created ${createdTabs.size} tab(s) for existing group $tabGroupId",
      )
    } yield createdTabs
      .foldLeft(state) { case (acc, (t, b)) =>
        acc.mapTab(t.id, b, tabGroupId)
      }
      .setFolder(tabGroupId, folder)
  }

  private def createTabs(
    bookmarks: List[BookmarkNode.Bookmark],
    existingTabs: List[Tab],
    tabGroupId: Option[TabGroup.Id],
  ): Future[List[(Tab, BookmarkNode.Bookmark)]] =
    Future
      .traverse(bookmarks)(createTab(existingTabs, tabGroupId))
      .map(_.flatten)
      .map { created =>
        val skipped = bookmarks.size - created.size
        Log(
          Source.StartupSync,
          s"Created ${created.size} tab(s), $skipped skipped",
        )
        created
      }

  private def createTab(existingTabs: List[Tab], tabGroupId: Option[TabGroup.Id])(
    bookmark: BookmarkNode.Bookmark,
  ) =
    planner.planTabCreation(bookmark, existingTabs, tabGroupId) match {
      case Left(e) => Future.failed(IllegalStateException(s"Cannot create tab: $e"))
      case Right(Some(request)) => tabs.create(request).map(_ -> bookmark).map(Some(_))
      case Right(None) => Future.successful(None)
    }

  private def createFoldersAndBookmarks(
    state: State,
    allGroupedTabs: List[GroupedTabs],
  ) =
    allGroupedTabs.foldLeft(Future.successful(state)) { (acc, groupedTabs) =>
      acc.flatMap(processTabGroupAndTabs(groupedTabs))
    }

  private def processTabGroupAndTabs(groupedTabs: GroupedTabs)(
    state: State,
  ): Future[State] =
    planner.planFolderCreation(state, groupedTabs.tabGroup) match {
      case Left(e) => Future.failed(IllegalStateException(s"Cannot create folder: $e"))
      case Right(None) => processTabs(state, groupedTabs)
      case Right(Some(request)) =>
        bookmarks.createFolder(request).flatMap { folder =>
          val updatedState = state.setFolder(groupedTabs.tabGroup.id, folder)
          processTabs(updatedState, groupedTabs)
        }
    }

  private def processTabs(
    state: State,
    groupedTabs: GroupedTabs,
  ): Future[State] = {
    val GroupedTabs(tabGroup, tabs) = groupedTabs
    tabs.foldLeft(Future.successful(state)) { (acc, t) =>
      acc.flatMap(processTab(tabGroup.id, t))
    }
  }

  private def processTab(tabGroupId: TabGroup.Id, tab: Tab)(state: State): Future[State] =
    planner.planBookmarkCreation(state, tab, tabGroupId) match {
      case Left(e) => Future.failed(IllegalStateException(s"Cannot create bookmark: $e"))
      case Right(None) => Future.successful(state)
      case Right(Some(request)) =>
        bookmarks.createBookmark(request).map(state.mapTab(tab.id, _, tabGroupId))
    }

}

object StartupSync {
  private final case class GroupedTabs(tabGroup: TabGroup, tabs: List[Tab])

  private final case class FolderWithBookmarks(
    folder: BookmarkNode.Folder,
    bookmarks: List[BookmarkNode.Bookmark],
  )

}
