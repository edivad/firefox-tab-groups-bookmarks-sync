import mock.MockBookmarks.Snapshot
import mock.{MockBookmarks, MockSessionStore, MockTabGroups, MockTabs}
import tabgroupsbookmarkssync.config.Config
import tabgroupsbookmarkssync.model.*
import tabgroupsbookmarkssync.service.Bootstrap
import tabgroupsbookmarkssync.spi.Bookmarks
import util.Fixtures.*

import scala.collection.mutable
import scala.concurrent.ExecutionContext

class BootstrapSuite extends munit.FunSuite {

  given ec: ExecutionContext = ExecutionContext.global

  private def rootFolderOf(config: Config): BookmarkNode.Folder =
    folder(parentId = config.otherBookmarksId, title = Some(config.syncFolderName))

  test("reuses the persisted state for the current root") {
    // given
    val cfg = config()
    val root = rootFolderOf(cfg)
    val tgId = TabGroup.Id("1")
    val f = folder(parentId = root.id)
    val persisted = SessionState(root.id, newState(foldersByTabGroupId = Map(tgId -> f)))
    val bookmarks = MockBookmarks(getChildrenResponses = mutable.ListBuffer(List(root)))
    val tabGroups = MockTabGroups()
    val tabs = MockTabs()
    val store = MockSessionStore(initial = Some(persisted))
    val underTest = Bootstrap(cfg, bookmarks, tabGroups, tabs, store)

    // when
    val result = underTest.resolve()

    // then
    result.map { session =>
      assertEquals(session, persisted)
      assertEquals(
        bookmarks.snapshot,
        Snapshot(getChildren = List(Bookmarks.GetChildren(id = cfg.otherBookmarksId))),
      )
      assertEquals(tabGroups.snapshot, MockTabGroups.Snapshot())
      assertEquals(tabs.snapshot, MockTabs.Snapshot())
      assertEquals(store.snapshot.save, List.empty)
    }
  }

  test("reconciles bookmarks when nothing is persisted") {
    // given
    val cfg = config()
    val root = rootFolderOf(cfg)
    val bookmarks = MockBookmarks(
      getChildrenResponses = mutable.ListBuffer(List(root), List.empty[BookmarkNode]),
    )
    val tabGroups = MockTabGroups(queryResponses = mutable.ListBuffer(List.empty))
    val tabs = MockTabs(queryResponses = mutable.ListBuffer(List.empty))
    val store = MockSessionStore()
    val underTest = Bootstrap(cfg, bookmarks, tabGroups, tabs, store)

    // when
    val result = underTest.resolve()

    // then
    result.map { session =>
      val expected = SessionState(root.id, newState())
      assertEquals(session, expected)
      assertEquals(store.snapshot.save, List(expected))
      assertEquals(store.snapshot.clear, List.empty)
    }
  }

  test("discards the persisted state when the root changed") {
    // given
    val cfg = config()
    val root = rootFolderOf(cfg)
    val stale = SessionState(
      BookmarkNode.Id("missing__"),
      newState(foldersByTabGroupId = Map(TabGroup.Id("1") -> folder())),
    )
    val bookmarks = MockBookmarks(
      getChildrenResponses = mutable.ListBuffer(List(root), List.empty[BookmarkNode]),
    )
    val tabGroups = MockTabGroups(queryResponses = mutable.ListBuffer(List.empty))
    val tabs = MockTabs(queryResponses = mutable.ListBuffer(List.empty))
    val store = MockSessionStore(initial = Some(stale))
    val underTest = Bootstrap(cfg, bookmarks, tabGroups, tabs, store)

    // when
    val result = underTest.resolve()

    // then
    result.map { session =>
      val expected = SessionState(root.id, newState())
      assertEquals(session, expected)
      assertEquals(store.snapshot.clear, List(()))
      assertEquals(store.snapshot.save, List(expected))
    }
  }

  test("reconciles bookmarks when the persisted state cannot be loaded") {
    // given
    val cfg = config()
    val root = rootFolderOf(cfg)
    val bookmarks = MockBookmarks(
      getChildrenResponses = mutable.ListBuffer(List(root), List.empty[BookmarkNode]),
    )
    val tabGroups = MockTabGroups(queryResponses = mutable.ListBuffer(List.empty))
    val tabs = MockTabs(queryResponses = mutable.ListBuffer(List.empty))
    val store = MockSessionStore(loadFailures = mutable.ListBuffer(Exception("failed")))
    val underTest = Bootstrap(cfg, bookmarks, tabGroups, tabs, store)

    // when
    val result = underTest.resolve()

    // then
    result.map { session =>
      assertEquals(session, SessionState(root.id, newState()))
      assertEquals(store.snapshot.clear, List.empty)
    }
  }

  test("keeps the reconciled state when saving it fails") {
    // given
    val cfg = config()
    val root = rootFolderOf(cfg)
    val bookmarks = MockBookmarks(
      getChildrenResponses = mutable.ListBuffer(List(root), List.empty[BookmarkNode]),
    )
    val tabGroups = MockTabGroups(queryResponses = mutable.ListBuffer(List.empty))
    val tabs = MockTabs(queryResponses = mutable.ListBuffer(List.empty))
    val store = MockSessionStore(saveFailures = mutable.ListBuffer(Exception("failed")))
    val underTest = Bootstrap(cfg, bookmarks, tabGroups, tabs, store)

    // when
    val result = underTest.resolve()

    // then
    result.map { session =>
      assertEquals(session, SessionState(root.id, newState()))
    }
  }

}
