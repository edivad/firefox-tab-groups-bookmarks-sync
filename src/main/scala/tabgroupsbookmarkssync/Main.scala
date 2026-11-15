package tabgroupsbookmarkssync

import tabgroupsbookmarkssync.config.Config
import tabgroupsbookmarkssync.log.{Log, Source}
import tabgroupsbookmarkssync.service.SyncRuntime
import tabgroupsbookmarkssync.spi.browser.{
  BrowserBookmarks,
  BrowserSessionStore,
  BrowserTabGroups,
  BrowserTabs,
}

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

@main def main(): Unit = {
  Log(Source.Main, "Tab Groups & Bookmarks Sync started")

  given ExecutionContext = ExecutionContext.global

  val runtime = SyncRuntime(
    config = Config.default,
    bookmarks = BrowserBookmarks(),
    tabGroups = BrowserTabGroups(),
    tabs = BrowserTabs(),
    store = BrowserSessionStore(),
  )

  runtime.start()

  runtime.whenIdle().onComplete {
    case Success(_) => Log(Source.Main, "Extension successfully started")
    case Failure(exception) => Log(Source.Main, s"Error during initialization: $exception")
  }
}
