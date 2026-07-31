package tabgroupsbookmarkssync

import tabgroupsbookmarkssync.config.Config
import tabgroupsbookmarkssync.log.{Log, Source}
import tabgroupsbookmarkssync.service.{EventSync, Initializer, StartupSync, SyncPlanner}
import tabgroupsbookmarkssync.spi.browser.{BrowserBookmarks, BrowserTabGroups, BrowserTabs}

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

@main def main(): Unit = {
  Log(Source.Main, "Tab Groups & Bookmarks Sync started")

  given ExecutionContext = ExecutionContext.global

  val cfg = Config.default

  val bookmarks = BrowserBookmarks()
  val tabGroups = BrowserTabGroups()
  val tabs = BrowserTabs()

  val initializer = Initializer(cfg, bookmarks)

  val task = for {
    root <- initializer.ensureRootFolderIsPresent
    planner = SyncPlanner(root.id)
    startupSync = StartupSync(root.id, bookmarks, planner, tabGroups, tabs)
    state <- startupSync.synchronize()
  } yield {
    val eventSync = EventSync(root.id, state, bookmarks, planner, tabGroups, tabs)
    tabGroups.onTabGroupCreate(eventSync.tabGroupCreated)
    tabGroups.onTabGroupUpdate(eventSync.tabGroupUpdated)
    tabGroups.onTabGroupRemove(eventSync.tabGroupRemoved)
    tabs.onTabUpdate(eventSync.tabUpdated)
  }

  task.onComplete {
    case Success(_) => Log(Source.Main, "Extension successfully started")
    case Failure(exception) => Log(Source.Main, s"Error during initialization: $exception")
  }
}
