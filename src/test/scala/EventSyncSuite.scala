import mock.MockBookmarks.Snapshot
import mock.{MockBookmarks, MockSyncPlanner, MockTabGroups, MockTabs}
import tabgroupsbookmarkssync.model.*
import tabgroupsbookmarkssync.service.{EventSync, SyncError}
import tabgroupsbookmarkssync.spi.Bookmarks
import util.Fixtures.*

import scala.collection.mutable
import scala.concurrent.ExecutionContext

class EventSyncSuite extends munit.FunSuite {

  given ec: ExecutionContext = ExecutionContext.global

  val rootId = BookmarkNode.Id("root_____")

  test("creates folder for new tab group") {
    // given
    val f = folder(parentId = rootId)
    val tg = tabGroup()
    val bookmarks = MockBookmarks(createFolderResponses = mutable.ListBuffer(f))
    val state = newState()
    val planner = MockSyncPlanner(createFolderResponses =
      mutable.ListBuffer(Right(Some(Bookmarks.CreateFolder(parentId = rootId, title = tg.title)))),
    )
    val underTest =
      EventSync(rootId, state, bookmarks, planner, MockTabGroups.unused, MockTabs.unused)

    // when
    underTest.tabGroupCreated(tg)

    // then
    underTest.whenIdle().map { state =>
      assertEquals(
        bookmarks.snapshot,
        Snapshot(createFolder = List(Bookmarks.CreateFolder(parentId = rootId, title = tg.title))),
      )
      assertEquals(state, newState(foldersByTabGroupId = Map(tg.id -> f)))
    }
  }

  test("skips folder creation when folder already exists") {
    // given
    val f = folder(parentId = rootId)
    val tg = tabGroup()
    val bookmarks = MockBookmarks()
    val state = newState(foldersByTabGroupId = Map(tg.id -> f))
    val planner = MockSyncPlanner(createFolderResponses = mutable.ListBuffer(Right(None)))
    val underTest =
      EventSync(rootId, state, bookmarks, planner, MockTabGroups.unused, MockTabs.unused)

    // when
    underTest.tabGroupCreated(tg)

    // then
    underTest.whenIdle().map { state =>
      assertEquals(bookmarks.snapshot, Snapshot())
      assertEquals(state, newState(foldersByTabGroupId = Map(tg.id -> f)))
    }
  }

  test("updates folder when title changes") {
    // given
    val f = folder(parentId = rootId)
    val tg = tabGroup()
    val newTitle = title()
    val updatedFolder = folder(id = f.id, parentId = rootId, title = newTitle)
    val bookmarks = MockBookmarks(updateFolderResponses = mutable.ListBuffer(updatedFolder))
    val state = newState(foldersByTabGroupId = Map(tg.id -> f))
    val planner = MockSyncPlanner(updateFolderResponses =
      mutable.ListBuffer(Right(Bookmarks.UpdateFolder(id = f.id, title = newTitle))),
    )
    val underTest =
      EventSync(rootId, state, bookmarks, planner, MockTabGroups.unused, MockTabs.unused)

    // when
    underTest.tabGroupUpdated(tg.copy(title = newTitle))

    // then
    underTest.whenIdle().map { state =>
      assertEquals(
        bookmarks.snapshot,
        Snapshot(updateFolder = List(Bookmarks.UpdateFolder(id = f.id, title = newTitle))),
      )
      assertEquals(state, newState(foldersByTabGroupId = Map(tg.id -> updatedFolder)))
    }
  }

  test("fails when folder not found for update") {
    // given
    val tg = tabGroup()
    val bookmarks = MockBookmarks()
    val state = newState()
    val planner = MockSyncPlanner(updateFolderResponses =
      mutable.ListBuffer(Left(SyncError.FolderNotFoundForTabGroup(tg))),
    )
    val underTest =
      EventSync(rootId, state, bookmarks, planner, MockTabGroups.unused, MockTabs.unused)

    // when
    underTest.tabGroupUpdated(tg)

    // then
    underTest.whenIdle().map { state =>
      assertEquals(bookmarks.snapshot, Snapshot())
      assertEquals(state, newState())
    }
  }

  test("removes folder when tab group is removed") {
    // given
    val f = folder(parentId = rootId)
    val tg = tabGroup()
    val bm = bookmark(parentId = f.id)
    val t = tab(groupId = Some(tg.id))
    val bookmarks = MockBookmarks()
    val state = newState(
      foldersByTabGroupId = Map(tg.id -> f),
      bookmarksByTabId = Map(t.id -> bm),
      tabGroupsForTabs = Map(t.id -> tg.id),
    )
    val planner = MockSyncPlanner(removeFolderResponses =
      mutable.ListBuffer(Right(Bookmarks.RemoveFolder(id = f.id))),
    )
    val underTest =
      EventSync(rootId, state, bookmarks, planner, MockTabGroups.unused, MockTabs.unused)

    // when
    underTest.tabGroupRemoved(tg)

    // then
    underTest.whenIdle().map { state =>
      assertEquals(
        bookmarks.snapshot,
        Snapshot(removeFolder = List(Bookmarks.RemoveFolder(id = f.id))),
      )
      assertEquals(state, newState())
    }
  }

  test("fails when folder not found for removal") {
    // given
    val tg = tabGroup()
    val bookmarks = MockBookmarks()
    val state = newState()
    val planner = MockSyncPlanner(removeFolderResponses =
      mutable.ListBuffer(Left(SyncError.FolderNotFoundForTabGroup(tg))),
    )
    val underTest =
      EventSync(rootId, state, bookmarks, planner, MockTabGroups.unused, MockTabs.unused)

    // when
    underTest.tabGroupRemoved(tg)

    // then
    underTest.whenIdle().map { state =>
      assertEquals(bookmarks.snapshot, Snapshot())
      assertEquals(state, newState())
    }
  }

  test("different groups are processed independently") {
    // given
    val f1 = folder(parentId = rootId)
    val f2 = folder(parentId = rootId, title = title())
    val tg1 = tabGroup()
    val tg2 = tabGroup(title = title())
    val bookmarks = MockBookmarks(
      createFolderResponses = mutable.ListBuffer(f1, f2),
    )
    val state = newState()
    val planner = MockSyncPlanner(createFolderResponses =
      mutable.ListBuffer(
        Right(Some(Bookmarks.CreateFolder(parentId = rootId, title = tg1.title))),
        Right(Some(Bookmarks.CreateFolder(parentId = rootId, title = tg2.title))),
      ),
    )
    val underTest =
      EventSync(rootId, state, bookmarks, planner, MockTabGroups.unused, MockTabs.unused)

    // when
    underTest.tabGroupCreated(tg1)
    underTest.tabGroupCreated(tg2)

    // then
    underTest.whenIdle().map { state =>
      assertEquals(
        bookmarks.snapshot,
        Snapshot(createFolder =
          List(
            Bookmarks.CreateFolder(parentId = rootId, title = tg1.title),
            Bookmarks.CreateFolder(parentId = rootId, title = tg2.title),
          ),
        ),
      )
      assertEquals(state, newState(foldersByTabGroupId = Map(tg1.id -> f1, tg2.id -> f2)))
    }
  }

  test("creates bookmark when tab enters a group") {
    // given
    val f = folder(parentId = rootId)
    val tg = tabGroup()
    val t = tab(groupId = Some(tg.id))
    val bm = bookmark(parentId = f.id)
    val bookmarks = MockBookmarks(
      createBookmarkResponses = mutable.ListBuffer[BookmarkNode.Bookmark](bm),
    )
    val state = newState(foldersByTabGroupId = Map(tg.id -> f))
    val planner = MockSyncPlanner(createBookmarkResponses =
      mutable.ListBuffer(
        Right(Some(Bookmarks.CreateBookmark(parentId = f.id, title = t.title, url = t.url.get))),
      ),
    )
    val underTest =
      EventSync(rootId, state, bookmarks, planner, MockTabGroups.unused, MockTabs.unused)

    // when
    underTest.tabUpdated(t)

    // then
    underTest.whenIdle().map { state =>
      assertEquals(
        bookmarks.snapshot,
        Snapshot(createBookmark =
          List(Bookmarks.CreateBookmark(parentId = f.id, title = t.title, url = t.url.get)),
        ),
      )
      assertEquals(
        state,
        newState(
          foldersByTabGroupId = Map(tg.id -> f),
          bookmarksByTabId = Map(t.id -> bm),
          tabGroupsForTabs = Map(t.id -> tg.id),
        ),
      )
    }
  }

  test("skips bookmark when tab has no URL") {
    // given
    val f = folder(parentId = rootId)
    val tg = tabGroup()
    val t = tab(groupId = Some(tg.id), url = None)
    val bookmarks = MockBookmarks()
    val state = newState(foldersByTabGroupId = Map(tg.id -> f))
    val planner = MockSyncPlanner(createBookmarkResponses =
      mutable.ListBuffer(Left(SyncError.MissingUrlForTab(t))),
    )
    val underTest =
      EventSync(rootId, state, bookmarks, planner, MockTabGroups.unused, MockTabs.unused)

    // when
    underTest.tabUpdated(t)

    // then
    underTest.whenIdle().map { state =>
      assertEquals(bookmarks.snapshot, Snapshot())
      assertEquals(state, newState(foldersByTabGroupId = Map(tg.id -> f)))
    }
  }

  test("skips when folder not found for tab group") {
    // given
    val t = tab()
    val bookmarks = MockBookmarks()
    val state = newState()
    val planner = MockSyncPlanner(createBookmarkResponses =
      mutable.ListBuffer(Left(SyncError.FolderNotFoundForTabGroupId(t.groupId.get))),
    )
    val underTest =
      EventSync(rootId, state, bookmarks, planner, MockTabGroups.unused, MockTabs.unused)

    // when
    underTest.tabUpdated(t)

    // then
    underTest.whenIdle().map { state =>
      assertEquals(bookmarks.snapshot, Snapshot())
      assertEquals(state, newState())
    }
  }

  test("removes bookmark when tab leaves a group") {
    // given
    val f = folder(parentId = rootId)
    val tg = tabGroup()
    val bm = bookmark(parentId = f.id)
    val t = tab(groupId = Some(tg.id))
    val bookmarks = MockBookmarks()
    val state = newState(
      foldersByTabGroupId = Map(tg.id -> f),
      bookmarksByTabId = Map(t.id -> bm),
      tabGroupsForTabs = Map(t.id -> tg.id),
    )
    val planner = MockSyncPlanner(removeBookmarkResponses =
      mutable.ListBuffer(Right(Bookmarks.RemoveBookmark(id = bm.id))),
    )
    val underTest =
      EventSync(rootId, state, bookmarks, planner, MockTabGroups.unused, MockTabs.unused)

    // when
    underTest.tabUpdated(tab(id = t.id, groupId = None))

    // then
    underTest.whenIdle().map { state =>
      assertEquals(
        bookmarks.snapshot,
        Snapshot(removeBookmark = List(Bookmarks.RemoveBookmark(id = bm.id))),
      )
      assertEquals(state, newState(foldersByTabGroupId = Map(tg.id -> f)))
    }
  }

  test("removes bookmark from state when folder not found for tab leaving group") {
    // given
    val bm = bookmark()
    val t = tab()
    val bookmarks = MockBookmarks()
    val state =
      newState(bookmarksByTabId = Map(t.id -> bm), tabGroupsForTabs = Map(t.id -> t.groupId.get))
    val planner = MockSyncPlanner(removeBookmarkResponses =
      mutable.ListBuffer(Right(Bookmarks.RemoveBookmark(id = bm.id))),
    )
    val underTest =
      EventSync(rootId, state, bookmarks, planner, MockTabGroups.unused, MockTabs.unused)

    // when
    underTest.tabUpdated(tab(id = t.id, groupId = None))

    // then
    underTest.whenIdle().map { state =>
      assertEquals(
        bookmarks.snapshot,
        Snapshot(removeBookmark = List(Bookmarks.RemoveBookmark(id = bm.id))),
      )
      assertEquals(state, newState())
    }
  }

  test("fails when bookmark not found for tab leaving group") {
    // given
    val f = folder(parentId = rootId)
    val tg = tabGroup()
    val t = tab(groupId = Some(tg.id))
    val bookmarks = MockBookmarks()
    val state =
      newState(foldersByTabGroupId = Map(tg.id -> f), tabGroupsForTabs = Map(t.id -> tg.id))
    val planner = MockSyncPlanner(removeBookmarkResponses =
      mutable.ListBuffer(Left(SyncError.BookmarkNotFoundForTab(tab(id = t.id, groupId = None)))),
    )
    val underTest =
      EventSync(rootId, state, bookmarks, planner, MockTabGroups.unused, MockTabs.unused)

    // when
    underTest.tabUpdated(tab(id = t.id, groupId = None))

    // then
    underTest.whenIdle().map { state =>
      assertEquals(bookmarks.snapshot, Snapshot())
      assertEquals(
        state,
        newState(foldersByTabGroupId = Map(tg.id -> f), tabGroupsForTabs = Map(t.id -> tg.id)),
      )
    }
  }

  test("does nothing when tab stays in the same group") {
    // given
    val f = folder(parentId = rootId)
    val tg = tabGroup()
    val bm = bookmark(parentId = f.id)
    val t = tab(groupId = Some(tg.id))
    val bookmarks = MockBookmarks()
    val state = newState(
      foldersByTabGroupId = Map(tg.id -> f),
      bookmarksByTabId = Map(t.id -> bm),
      tabGroupsForTabs = Map(t.id -> tg.id),
    )
    val planner = MockSyncPlanner()
    val underTest =
      EventSync(rootId, state, bookmarks, planner, MockTabGroups.unused, MockTabs.unused)

    // when — same group as before
    underTest.tabUpdated(t)

    // then
    underTest.whenIdle().map { state =>
      assertEquals(bookmarks.snapshot, Snapshot())
      assertEquals(
        state,
        newState(
          foldersByTabGroupId = Map(tg.id -> f),
          bookmarksByTabId = Map(t.id -> bm),
          tabGroupsForTabs = Map(t.id -> tg.id),
        ),
      )
    }
  }

  test("tab update for a tab in a group waits for folder creation") {
    // given — no folder pre-populated in state
    val f = folder(parentId = rootId)
    val tg = tabGroup()
    val bm = bookmark(parentId = f.id)
    val t = tab(groupId = Some(tg.id))
    val bookmarks = MockBookmarks(
      createFolderResponses = mutable.ListBuffer(f),
      createBookmarkResponses = mutable.ListBuffer(bm),
    )
    val state = newState()
    val planner = MockSyncPlanner(
      createFolderResponses = mutable.ListBuffer(
        Right(Some(Bookmarks.CreateFolder(parentId = rootId, title = tg.title))),
      ),
      createBookmarkResponses = mutable.ListBuffer(
        Right(Some(Bookmarks.CreateBookmark(parentId = f.id, title = t.title, url = t.url.get))),
      ),
    )
    val underTest =
      EventSync(rootId, state, bookmarks, planner, MockTabGroups.unused, MockTabs.unused)

    // when — group creation and tab update back-to-back
    underTest.tabGroupCreated(tg)
    underTest.tabUpdated(t)

    // then — both complete successfully
    underTest.whenIdle().map { state =>
      assertEquals(
        bookmarks.snapshot,
        Snapshot(
          createFolder = List(Bookmarks.CreateFolder(parentId = rootId, title = tg.title)),
          createBookmark =
            List(Bookmarks.CreateBookmark(parentId = f.id, title = t.title, url = t.url.get)),
        ),
      )
      assertEquals(
        state,
        newState(
          foldersByTabGroupId = Map(tg.id -> f),
          bookmarksByTabId = Map(t.id -> bm),
          tabGroupsForTabs = Map(t.id -> tg.id),
        ),
      )
    }
  }

  test("moves bookmark when tab moves to a different group") {
    // given
    val f1 = folder(parentId = rootId)
    val f2 = folder(parentId = rootId, title = title())
    val tg1 = tabGroup()
    val tg2 = tabGroup(title = title())
    val bm1 = bookmark(parentId = f1.id)
    val bm2 = bookmark(parentId = f2.id)
    val t = tab(groupId = Some(tg1.id))
    val bookmarks = MockBookmarks(
      createBookmarkResponses = mutable.ListBuffer[BookmarkNode.Bookmark](bm2),
    )
    val state = newState(
      foldersByTabGroupId = Map(tg1.id -> f1, tg2.id -> f2),
      bookmarksByTabId = Map(t.id -> bm1),
      tabGroupsForTabs = Map(t.id -> tg1.id),
    )
    val planner = MockSyncPlanner(recreateBookmarkResponses =
      mutable.ListBuffer(
        Right(
          (
            Bookmarks.RemoveBookmark(id = bm1.id),
            Bookmarks.CreateBookmark(parentId = f2.id, title = t.title, url = t.url.get),
          ),
        ),
      ),
    )
    val underTest =
      EventSync(rootId, state, bookmarks, planner, MockTabGroups.unused, MockTabs.unused)

    // when
    underTest.tabUpdated(tab(id = t.id, groupId = Some(tg2.id)))

    // then
    underTest.whenIdle().map { state =>
      assertEquals(
        bookmarks.snapshot,
        Snapshot(
          removeBookmark = List(Bookmarks.RemoveBookmark(id = bm1.id)),
          createBookmark = List(
            Bookmarks.CreateBookmark(parentId = f2.id, title = t.title, url = t.url.get),
          ),
        ),
      )
      assertEquals(
        state,
        newState(
          foldersByTabGroupId = Map(tg1.id -> f1, tg2.id -> f2),
          bookmarksByTabId = Map(t.id -> bm2),
          tabGroupsForTabs = Map(t.id -> tg2.id),
        ),
      )
    }
  }

  test("moves bookmark when old folder not found for tab moving groups") {
    // given
    val f = folder(parentId = rootId, title = title())
    val tg = tabGroup(title = title())
    val bm = bookmark()
    val newBm = bookmark(parentId = f.id)
    val t = tab()
    val oldTgId = t.groupId.get
    val bookmarks = MockBookmarks(
      createBookmarkResponses = mutable.ListBuffer[BookmarkNode.Bookmark](newBm),
    )
    val state = newState(
      foldersByTabGroupId = Map(tg.id -> f),
      bookmarksByTabId = Map(t.id -> bm),
      tabGroupsForTabs = Map(t.id -> oldTgId),
    )
    val planner = MockSyncPlanner(recreateBookmarkResponses =
      mutable.ListBuffer(
        Right(
          (
            Bookmarks.RemoveBookmark(id = bm.id),
            Bookmarks.CreateBookmark(parentId = f.id, title = t.title, url = t.url.get),
          ),
        ),
      ),
    )
    val underTest =
      EventSync(rootId, state, bookmarks, planner, MockTabGroups.unused, MockTabs.unused)

    // when
    underTest.tabUpdated(tab(id = t.id, groupId = Some(tg.id)))

    // then
    underTest.whenIdle().map { state =>
      assertEquals(
        bookmarks.snapshot,
        Snapshot(
          removeBookmark = List(Bookmarks.RemoveBookmark(id = bm.id)),
          createBookmark = List(
            Bookmarks.CreateBookmark(parentId = f.id, title = t.title, url = t.url.get),
          ),
        ),
      )
      assertEquals(
        state,
        newState(
          foldersByTabGroupId = Map(tg.id -> f),
          bookmarksByTabId = Map(t.id -> newBm),
          tabGroupsForTabs = Map(t.id -> tg.id),
        ),
      )
    }
  }

  test("fails when new folder not found for tab moving groups") {
    // given
    val f = folder(parentId = rootId)
    val tg = tabGroup()
    val bm = bookmark(parentId = f.id)
    val t = tab(groupId = Some(tg.id))
    val bookmarks = MockBookmarks()
    val state = newState(
      foldersByTabGroupId = Map(tg.id -> f),
      bookmarksByTabId = Map(t.id -> bm),
      tabGroupsForTabs = Map(t.id -> tg.id),
    )
    val newTg = tabGroup()
    val planner = MockSyncPlanner(recreateBookmarkResponses =
      mutable.ListBuffer(Left(SyncError.FolderNotFoundForTabGroupId(newTg.id))),
    )
    val underTest =
      EventSync(rootId, state, bookmarks, planner, MockTabGroups.unused, MockTabs.unused)

    // when
    underTest.tabUpdated(tab(id = t.id, groupId = Some(newTg.id)))

    // then
    underTest.whenIdle().map { state =>
      assertEquals(bookmarks.snapshot, Snapshot())
      assertEquals(
        state,
        newState(
          foldersByTabGroupId = Map(tg.id -> f),
          bookmarksByTabId = Map(t.id -> bm),
          tabGroupsForTabs = Map(t.id -> tg.id),
        ),
      )
    }
  }

  test("fails when bookmark not found for tab moving groups") {
    // given
    val f1 = folder(parentId = rootId)
    val f2 = folder(parentId = rootId, title = title())
    val tg1 = tabGroup()
    val tg2 = tabGroup(title = title())
    val t = tab(groupId = Some(tg1.id))
    val bookmarks = MockBookmarks()
    val state = newState(
      foldersByTabGroupId = Map(tg1.id -> f1, tg2.id -> f2),
      tabGroupsForTabs = Map(t.id -> tg1.id),
    )
    val planner = MockSyncPlanner(recreateBookmarkResponses =
      mutable.ListBuffer(
        Left(SyncError.BookmarkNotFoundForTab(tab(id = t.id, groupId = Some(tg2.id)))),
      ),
    )
    val underTest =
      EventSync(rootId, state, bookmarks, planner, MockTabGroups.unused, MockTabs.unused)

    // when
    underTest.tabUpdated(tab(id = t.id, groupId = Some(tg2.id)))

    // then
    underTest.whenIdle().map { state =>
      assertEquals(bookmarks.snapshot, Snapshot())
      assertEquals(
        state,
        newState(
          foldersByTabGroupId = Map(tg1.id -> f1, tg2.id -> f2),
          tabGroupsForTabs = Map(t.id -> tg1.id),
        ),
      )
    }
  }

  test("skips when tab not in any group") {
    // given
    val bookmarks = MockBookmarks()
    val state = newState()
    val planner = MockSyncPlanner()
    val underTest =
      EventSync(rootId, state, bookmarks, planner, MockTabGroups.unused, MockTabs.unused)

    // when
    underTest.tabUpdated(tab(groupId = None))

    // then
    underTest.whenIdle().map { state =>
      assertEquals(bookmarks.snapshot, Snapshot())
      assertEquals(state, newState())
    }
  }

  test("recovers state when removeBookmark fails because bookmark was already deleted") {
    // given
    val f = folder(parentId = rootId)
    val tg = tabGroup()
    val bm = bookmark(parentId = f.id)
    val t = tab(groupId = Some(tg.id))
    val bookmarks = MockBookmarks(removeBookmarkFailures = mutable.ListBuffer(Exception("failed")))
    val state = newState(
      foldersByTabGroupId = Map(tg.id -> f),
      bookmarksByTabId = Map(t.id -> bm),
      tabGroupsForTabs = Map(t.id -> tg.id),
    )
    val planner = MockSyncPlanner(removeBookmarkResponses =
      mutable.ListBuffer(Right(Bookmarks.RemoveBookmark(id = bm.id))),
    )
    val underTest =
      EventSync(rootId, state, bookmarks, planner, MockTabGroups.unused, MockTabs.unused)

    // when
    underTest.tabUpdated(tab(id = t.id, groupId = None))

    // then
    underTest.whenIdle().map { state =>
      assertEquals(
        bookmarks.snapshot,
        Snapshot(removeBookmark = List(Bookmarks.RemoveBookmark(id = bm.id))),
      )
      assertEquals(state, newState(foldersByTabGroupId = Map(tg.id -> f)))
    }
  }

  test("recovers from bookmark failure during group move") {
    // given
    val f1 = folder(parentId = rootId)
    val f2 = folder(parentId = rootId, title = title())
    val tg1 = tabGroup()
    val tg2 = tabGroup(title = title())
    val bm1 = bookmark(parentId = f1.id)
    val bm2 = bookmark(parentId = f2.id)
    val t = tab(groupId = Some(tg1.id))
    val bookmarks = MockBookmarks(
      createBookmarkResponses = mutable.ListBuffer[BookmarkNode.Bookmark](bm2),
      removeBookmarkFailures = mutable.ListBuffer(Exception("failed")),
    )
    val state = newState(
      foldersByTabGroupId = Map(tg1.id -> f1, tg2.id -> f2),
      bookmarksByTabId = Map(t.id -> bm1),
      tabGroupsForTabs = Map(t.id -> tg1.id),
    )
    val planner = MockSyncPlanner(recreateBookmarkResponses =
      mutable.ListBuffer(
        Right(
          (
            Bookmarks.RemoveBookmark(id = bm1.id),
            Bookmarks.CreateBookmark(parentId = f2.id, title = t.title, url = t.url.get),
          ),
        ),
      ),
    )
    val underTest =
      EventSync(rootId, state, bookmarks, planner, MockTabGroups.unused, MockTabs.unused)

    // when
    underTest.tabUpdated(tab(id = t.id, groupId = Some(tg2.id)))

    // then
    underTest.whenIdle().map { state =>
      assertEquals(
        bookmarks.snapshot,
        Snapshot(
          removeBookmark = List(Bookmarks.RemoveBookmark(id = bm1.id)),
          createBookmark = List(
            Bookmarks.CreateBookmark(parentId = f2.id, title = t.title, url = t.url.get),
          ),
        ),
      )
      assertEquals(
        state,
        newState(
          foldersByTabGroupId = Map(tg1.id -> f1, tg2.id -> f2),
          bookmarksByTabId = Map(t.id -> bm2),
          tabGroupsForTabs = Map(t.id -> tg2.id),
        ),
      )
    }
  }

  test("fails when folder not found in state after planner skips creation") {
    // given
    val tg = tabGroup()
    val bookmarks = MockBookmarks()
    val state = newState()
    val planner = MockSyncPlanner(createFolderResponses = mutable.ListBuffer(Right(None)))
    val underTest =
      EventSync(rootId, state, bookmarks, planner, MockTabGroups.unused, MockTabs.unused)

    // when
    underTest.tabGroupCreated(tg)

    // then
    underTest.whenIdle().map { state =>
      assertEquals(bookmarks.snapshot, Snapshot())
      assertEquals(state, newState())
    }
  }

  test("fails when bookmark not found in state after planner skips creation") {
    // given
    val f = folder(parentId = rootId)
    val tg = tabGroup()
    val t = tab(groupId = Some(tg.id))
    val bookmarks = MockBookmarks()
    val state = newState(foldersByTabGroupId = Map(tg.id -> f))
    val planner = MockSyncPlanner(createBookmarkResponses = mutable.ListBuffer(Right(None)))
    val underTest =
      EventSync(rootId, state, bookmarks, planner, MockTabGroups.unused, MockTabs.unused)

    // when
    underTest.tabUpdated(t)

    // then
    underTest.whenIdle().map { state =>
      assertEquals(bookmarks.snapshot, Snapshot())
      assertEquals(
        state,
        newState(foldersByTabGroupId = Map(tg.id -> f)),
      )
    }
  }

}
