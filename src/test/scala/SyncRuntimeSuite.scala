import mock.MockBookmarks.Snapshot
import mock.{MockBookmarks, MockSessionStore, MockTabGroups, MockTabs}
import tabgroupsbookmarkssync.config.Config
import tabgroupsbookmarkssync.model.*
import tabgroupsbookmarkssync.service.SyncRuntime
import tabgroupsbookmarkssync.spi.Bookmarks
import util.Fixtures.*

import scala.collection.mutable
import scala.concurrent.ExecutionContext

class SyncRuntimeSuite extends munit.FunSuite {

  given ec: ExecutionContext = ExecutionContext.global

  private def rootFolderOf(config: Config): BookmarkNode.Folder =
    folder(parentId = config.otherBookmarksId, title = Some(config.syncFolderName))

  test("registers listeners before initialization completes") {
    // given
    val cfg = config()
    val root = rootFolderOf(cfg)
    val bookmarks = MockBookmarks(
      getChildrenResponses = mutable.ListBuffer(List(root), List.empty[BookmarkNode]),
    )
    val tabGroups = MockTabGroups(queryResponses = mutable.ListBuffer(List.empty))
    val tabs = MockTabs(queryResponses = mutable.ListBuffer(List.empty))
    val underTest = SyncRuntime(cfg, bookmarks, tabGroups, tabs, MockSessionStore())

    // when
    underTest.start()

    // then
    assertEquals(
      tabGroups.snapshot.listeners,
      MockTabGroups.Listeners(created = 1, updated = 1, removed = 1),
    )
    assertEquals(tabs.snapshot.listeners, MockTabs.Listeners(updated = 1))
  }

  test("processes events received during initialization in arrival order") {
    // given
    val cfg = config()
    val root = rootFolderOf(cfg)
    val tg1 = tabGroup()
    val tg2 = tabGroup(title = title())
    val f1 = folder(parentId = root.id)
    val f2 = folder(parentId = root.id, title = title())
    val bookmarks = MockBookmarks(
      getChildrenResponses = mutable.ListBuffer(List(root), List.empty[BookmarkNode]),
      createFolderResponses = mutable.ListBuffer(f1, f2),
    )
    val tabGroups = MockTabGroups(queryResponses = mutable.ListBuffer(List.empty))
    val tabs = MockTabs(queryResponses = mutable.ListBuffer(List.empty))
    val store = MockSessionStore()
    val underTest = SyncRuntime(cfg, bookmarks, tabGroups, tabs, store)

    // when
    underTest.start()
    tabGroups.fireCreated(tg1)
    tabGroups.fireCreated(tg2)

    // then
    underTest.whenIdle().map { _ =>
      assertEquals(
        bookmarks.snapshot,
        Snapshot(
          getChildren = List(
            Bookmarks.GetChildren(id = cfg.otherBookmarksId),
            Bookmarks.GetChildren(id = root.id),
          ),
          createFolder = List(
            Bookmarks.CreateFolder(parentId = root.id, title = tg1.title),
            Bookmarks.CreateFolder(parentId = root.id, title = tg2.title),
          ),
        ),
      )
      assertEquals(
        store.snapshot.save,
        List(
          SessionState(root.id, newState()),
          SessionState(root.id, newState(foldersByTabGroupId = Map(tg1.id -> f1))),
          SessionState(
            root.id,
            newState(foldersByTabGroupId = Map(tg1.id -> f1, tg2.id -> f2)),
          ),
        ),
      )
    }
  }

  test("processes events received after initialization") {
    // given
    val cfg = config()
    val root = rootFolderOf(cfg)
    val tg = tabGroup()
    val f = folder(parentId = root.id)
    val bookmarks = MockBookmarks(
      getChildrenResponses = mutable.ListBuffer(List(root), List.empty[BookmarkNode]),
      createFolderResponses = mutable.ListBuffer(f),
    )
    val tabGroups = MockTabGroups(queryResponses = mutable.ListBuffer(List.empty))
    val tabs = MockTabs(queryResponses = mutable.ListBuffer(List.empty))
    val underTest = SyncRuntime(cfg, bookmarks, tabGroups, tabs, MockSessionStore())

    // when
    underTest.start()

    // then
    underTest.whenIdle().flatMap { _ =>
      tabGroups.fireCreated(tg)
      underTest.whenIdle().map { _ =>
        assertEquals(
          bookmarks.snapshot.createFolder,
          List(Bookmarks.CreateFolder(parentId = root.id, title = tg.title)),
        )
      }
    }
  }

  test("keeps listeners registered when initialization fails") {
    // given
    val failure = Exception("failed")
    val bookmarks = MockBookmarks(getChildrenFailures = mutable.ListBuffer(failure))
    val tabGroups = MockTabGroups()
    val tabs = MockTabs()
    val underTest = SyncRuntime(config(), bookmarks, tabGroups, tabs, MockSessionStore())

    // when
    underTest.start()

    // then
    assertEquals(
      tabGroups.snapshot.listeners,
      MockTabGroups.Listeners(created = 1, updated = 1, removed = 1),
    )
    assertEquals(tabs.snapshot.listeners, MockTabs.Listeners(updated = 1))
    tabGroups.fireCreated(tabGroup())
    underTest.whenIdle().failed.map(error => assertEquals(error, failure))
  }

  test("applies the waking rename to the restored folder without creating duplicates") {
    // given
    val cfg = config()
    val root = rootFolderOf(cfg)
    val oldTitle = title()
    val newTitle = title()
    val tg = tabGroup(title = oldTitle)
    val f = folder(parentId = root.id, title = oldTitle)
    val renamed = folder(id = f.id, parentId = root.id, title = newTitle)
    val persisted = SessionState(root.id, newState(foldersByTabGroupId = Map(tg.id -> f)))
    val bookmarks = MockBookmarks(
      getChildrenResponses = mutable.ListBuffer(List(root)),
      updateFolderResponses = mutable.ListBuffer(renamed),
    )
    val tabGroups = MockTabGroups()
    val tabs = MockTabs()
    val store = MockSessionStore(initial = Some(persisted))
    val underTest = SyncRuntime(cfg, bookmarks, tabGroups, tabs, store)

    // when
    underTest.start()
    tabGroups.fireUpdated(tg.copy(title = newTitle))

    // then
    underTest.whenIdle().map { _ =>
      assertEquals(
        bookmarks.snapshot,
        Snapshot(
          getChildren = List(Bookmarks.GetChildren(id = cfg.otherBookmarksId)),
          updateFolder = List(Bookmarks.UpdateFolder(id = f.id, title = newTitle)),
        ),
      )
      assertEquals(
        tabGroups.snapshot,
        MockTabGroups.Snapshot(listeners = MockTabGroups.Listeners(1, 1, 1)),
      )
      assertEquals(
        store.snapshot.save,
        List(SessionState(root.id, newState(foldersByTabGroupId = Map(tg.id -> renamed)))),
      )
    }
  }

}
