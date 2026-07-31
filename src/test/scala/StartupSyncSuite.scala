import mock.MockBookmarks.Snapshot
import mock.{MockBookmarks, MockSyncPlanner, MockTabGroups, MockTabs}
import tabgroupsbookmarkssync.service.SyncPlanner
import tabgroupsbookmarkssync.model.*
import tabgroupsbookmarkssync.service.{StartupSync, SyncError}
import tabgroupsbookmarkssync.spi.{Bookmarks, TabGroups, Tabs}
import util.Fixtures.*

import scala.collection.mutable
import scala.concurrent.ExecutionContext

class StartupSyncSuite extends munit.FunSuite {

  given ec: ExecutionContext = ExecutionContext.global

  val rootId = BookmarkNode.Id("root_____")

  test("does nothing when no bookmark folders exist") {
    // given
    val tg1 = tabGroup()
    val tg2 = tabGroup()
    val bookmarks = MockBookmarks(
      getChildrenResponses = mutable.ListBuffer(List.empty[BookmarkNode]),
    )
    val planner = MockSyncPlanner(
      createFolderResponses = mutable.ListBuffer(Right(None), Right(None)),
    )
    val tabGroups = MockTabGroups(
      queryResponses = mutable.ListBuffer(List(tg1, tg2)),
    )
    val tabs = MockTabs(
      queryResponses = mutable.ListBuffer(List()),
    )
    val underTest = StartupSync(rootId, bookmarks, planner, tabGroups, tabs)

    // when
    val result = underTest.synchronize()

    // then
    result.map { state =>
      assertEquals(state, newState())
      assertEquals(
        bookmarks.snapshot,
        Snapshot(getChildren = List(Bookmarks.GetChildren(rootId))),
      )
      assertEquals(tabGroups.snapshot, MockTabGroups.Snapshot())
      assertEquals(tabs.snapshot, MockTabs.Snapshot())
    }
  }

  test("does nothing when no bookmark folders exist but tabs are grouped") {
    // given
    val tg = tabGroup()
    val tab1 = tab(groupId = Some(tg.id))
    val tab2 = tab(groupId = None)
    val bookmarks = MockBookmarks(
      getChildrenResponses = mutable.ListBuffer(List.empty[BookmarkNode]),
    )
    val planner = MockSyncPlanner(
      createFolderResponses = mutable.ListBuffer(Right(None)),
      createBookmarkResponses = mutable.ListBuffer(Right(None)),
    )
    val tabGroups = MockTabGroups(queryResponses = mutable.ListBuffer(List(tg)))
    val tabs = MockTabs(queryResponses = mutable.ListBuffer(List(tab1, tab2)))
    val underTest = StartupSync(rootId, bookmarks, planner, tabGroups, tabs)

    // when
    val result = underTest.synchronize()

    // then
    result.map { state =>
      assertEquals(state, newState())
      assertEquals(
        bookmarks.snapshot,
        Snapshot(getChildren = List(Bookmarks.GetChildren(rootId))),
      )
      assertEquals(tabGroups.snapshot, MockTabGroups.Snapshot())
      assertEquals(tabs.snapshot, MockTabs.Snapshot())
    }
  }

  test("creates new group and tabs from folder") {
    // given
    val fdr = folder()
    val bm = bookmark()
    val bmUrl = bm.url.get
    val tgId = TabGroup.Id("1")
    val t = tab(id = Tab.Id("1"), groupId = Some(tgId))
    val updatedGroup = tabGroup(id = tgId, title = fdr.title)
    val bookmarks = MockBookmarks(
      getChildrenResponses = mutable.ListBuffer(List(fdr), List(bm)),
    )
    val planner = MockSyncPlanner(
      planTabGroupCreationResponses = mutable.ListBuffer(Right(None)),
      planTabCreationResponses = mutable.ListBuffer(
        Right(Some(Tabs.CreateTab(url = bmUrl, groupId = None))),
      ),
    )
    val tabGroups = MockTabGroups(
      queryResponses = mutable.ListBuffer(List.empty),
      updateResponses = mutable.ListBuffer(updatedGroup),
    )
    val tabs = MockTabs(
      queryResponses = mutable.ListBuffer(List.empty),
      createResponses = mutable.ListBuffer(t),
      groupResponses = mutable.ListBuffer(tgId),
    )
    val underTest = StartupSync(rootId, bookmarks, planner, tabGroups, tabs)

    // when
    val result = underTest.synchronize()

    // then
    result.map { state =>
      assert(state.foldersByTabGroupId == Map(tgId -> fdr))
      assert(state.bookmarksByTabId == Map(t.id -> bm))
      assert(state.tabGroupsForTabs == Map(t.id -> tgId))
      assertEquals(
        bookmarks.snapshot,
        Snapshot(
          getChildren = List(
            Bookmarks.GetChildren(rootId),
            Bookmarks.GetChildren(fdr.id),
          ),
        ),
      )
      assertEquals(
        tabs.snapshot,
        MockTabs.Snapshot(
          create = List(Tabs.CreateTab(url = bmUrl, groupId = None)),
          group = List(Tabs.GroupTabs(List(t.id), None)),
        ),
      )
      assertEquals(
        tabGroups.snapshot,
        MockTabGroups.Snapshot(
          update = List(TabGroups.UpdateTabGroup(tgId, fdr.title)),
        ),
      )
    }
  }

  test("adds tabs to existing group from folder") {
    // given
    val fdr = folder()
    val bm = bookmark()
    val bmUrl = bm.url.get
    val tgId = TabGroup.Id("1")
    val t = tab(id = Tab.Id("1"), groupId = Some(tgId))
    val bookmarks = MockBookmarks(
      getChildrenResponses = mutable.ListBuffer(List(fdr), List(bm)),
    )
    val planner = MockSyncPlanner(
      planTabGroupCreationResponses = mutable.ListBuffer(Right(Some(tgId))),
      planTabCreationResponses = mutable.ListBuffer(
        Right(Some(Tabs.CreateTab(url = bmUrl, groupId = Some(tgId)))),
      ),
    )
    val tabGroups = MockTabGroups(
      queryResponses = mutable.ListBuffer(List.empty),
    )
    val tabs = MockTabs(
      queryResponses = mutable.ListBuffer(List.empty),
      createResponses = mutable.ListBuffer(t),
    )
    val underTest = StartupSync(rootId, bookmarks, planner, tabGroups, tabs)

    // when
    val result = underTest.synchronize()

    // then
    result.map { state =>
      assert(state.foldersByTabGroupId == Map(tgId -> fdr))
      assert(state.bookmarksByTabId == Map(t.id -> bm))
      assert(state.tabGroupsForTabs == Map(t.id -> tgId))
      assertEquals(
        bookmarks.snapshot,
        Snapshot(
          getChildren = List(
            Bookmarks.GetChildren(rootId),
            Bookmarks.GetChildren(fdr.id),
          ),
        ),
      )
      assertEquals(
        tabs.snapshot,
        MockTabs.Snapshot(
          create = List(Tabs.CreateTab(url = bmUrl, groupId = Some(tgId))),
          group = List.empty,
        ),
      )
      assertEquals(tabGroups.snapshot, MockTabGroups.Snapshot())
    }
  }

  test("creates empty group when folder has no bookmarks") {
    // given
    val fdr = folder()
    val tgId = TabGroup.Id("1")
    val updatedGroup = tabGroup(id = tgId, title = fdr.title)
    val bookmarks = MockBookmarks(
      getChildrenResponses = mutable.ListBuffer(List(fdr), List.empty),
    )
    val planner = MockSyncPlanner(
      planTabGroupCreationResponses = mutable.ListBuffer(Right(None)),
      planTabCreationResponses = mutable.ListBuffer.empty,
    )
    val tabGroups = MockTabGroups(
      queryResponses = mutable.ListBuffer(List.empty),
      updateResponses = mutable.ListBuffer(updatedGroup),
    )
    val tabs = MockTabs(
      queryResponses = mutable.ListBuffer(List.empty),
      groupResponses = mutable.ListBuffer(tgId),
    )
    val underTest = StartupSync(rootId, bookmarks, planner, tabGroups, tabs)

    // when
    val result = underTest.synchronize()

    // then
    result.map { state =>
      assert(state.foldersByTabGroupId == Map(tgId -> fdr))
      assert(state.bookmarksByTabId == Map.empty)
      assert(state.tabGroupsForTabs == Map.empty)
      assertEquals(
        bookmarks.snapshot,
        Snapshot(
          getChildren = List(
            Bookmarks.GetChildren(rootId),
            Bookmarks.GetChildren(fdr.id),
          ),
        ),
      )
      assertEquals(
        tabs.snapshot,
        MockTabs.Snapshot(
          create = List.empty,
          group = List(Tabs.GroupTabs(List.empty, None)),
        ),
      )
      assertEquals(
        tabGroups.snapshot,
        MockTabGroups.Snapshot(
          update = List(TabGroups.UpdateTabGroup(tgId, fdr.title)),
        ),
      )
    }
  }

  test("skips tab creation when already exists") {
    // given
    val fdr = folder()
    val tgId = TabGroup.Id("1")
    val bookmarks = MockBookmarks(
      getChildrenResponses = mutable.ListBuffer(List(fdr), List(bookmark())),
    )
    val planner = MockSyncPlanner(
      planTabGroupCreationResponses = mutable.ListBuffer(Right(Some(tgId))),
      planTabCreationResponses = mutable.ListBuffer(Right(None)),
    )
    val tabGroups = MockTabGroups(
      queryResponses = mutable.ListBuffer(List.empty),
    )
    val tabs = MockTabs(
      queryResponses = mutable.ListBuffer(List.empty),
    )
    val underTest = StartupSync(rootId, bookmarks, planner, tabGroups, tabs)

    // when
    val result = underTest.synchronize()

    // then
    result.map { state =>
      assert(state.foldersByTabGroupId == Map(tgId -> fdr))
      assert(state.bookmarksByTabId == Map.empty)
      assert(state.tabGroupsForTabs == Map.empty)
      assertEquals(
        bookmarks.snapshot,
        Snapshot(
          getChildren = List(
            Bookmarks.GetChildren(rootId),
            Bookmarks.GetChildren(fdr.id),
          ),
        ),
      )
      assertEquals(tabs.snapshot, MockTabs.Snapshot())
      assertEquals(tabGroups.snapshot, MockTabGroups.Snapshot())
    }
  }

  test("propagates error when tab creation fails") {
    // given
    val fdr = folder()
    val tgId = TabGroup.Id("1")
    val bookmarks = MockBookmarks(
      getChildrenResponses = mutable.ListBuffer(List(fdr), List(bookmark())),
    )
    val planner = MockSyncPlanner(
      planTabGroupCreationResponses = mutable.ListBuffer(Right(Some(tgId))),
      planTabCreationResponses = mutable.ListBuffer(
        Left(SyncError.MissingUrlForTab(tab(url = None))),
      ),
    )
    val tabGroups = MockTabGroups(
      queryResponses = mutable.ListBuffer(List.empty),
    )
    val tabs = MockTabs(
      queryResponses = mutable.ListBuffer(List.empty),
    )
    val underTest = StartupSync(rootId, bookmarks, planner, tabGroups, tabs)

    // when
    val result = underTest.synchronize()

    // then
    result.failed.map { e =>
      assert(e.isInstanceOf[IllegalStateException])
      assert(e.getMessage.contains("Cannot create tab"))
      assertEquals(
        bookmarks.snapshot,
        Snapshot(
          getChildren = List(
            Bookmarks.GetChildren(rootId),
            Bookmarks.GetChildren(fdr.id),
          ),
        ),
      )
      assertEquals(tabs.snapshot, MockTabs.Snapshot())
      assertEquals(tabGroups.snapshot, MockTabGroups.Snapshot())
    }
  }

  test("propagates error when tab group creation fails") {
    // given
    val fdr = folder()
    val bookmarks = MockBookmarks(
      getChildrenResponses = mutable.ListBuffer(List(fdr), List.empty),
    )
    val planner = MockSyncPlanner(
      planTabGroupCreationResponses = mutable.ListBuffer(
        Left(SyncError.FolderAlreadyPresent(fdr, tabGroup())),
      ),
    )
    val tabGroups = MockTabGroups(
      queryResponses = mutable.ListBuffer(List.empty),
    )
    val tabs = MockTabs(
      queryResponses = mutable.ListBuffer(List.empty),
    )
    val underTest = StartupSync(rootId, bookmarks, planner, tabGroups, tabs)

    // when
    val result = underTest.synchronize()

    // then
    result.failed.map { e =>
      assert(e.isInstanceOf[IllegalStateException])
      assert(e.getMessage.contains("Cannot create tab group"))
      assertEquals(
        bookmarks.snapshot,
        Snapshot(
          getChildren = List(
            Bookmarks.GetChildren(rootId),
            Bookmarks.GetChildren(fdr.id),
          ),
        ),
      )
      assertEquals(tabs.snapshot, MockTabs.Snapshot())
      assertEquals(tabGroups.snapshot, MockTabGroups.Snapshot())
    }
  }

  test("processes multiple folders and accumulates state") {
    // given
    val fdr1 = folder()
    val fdr2 = folder()
    val bm1 = bookmark()
    val bm2 = bookmark()
    val tgId1 = TabGroup.Id("1")
    val tgId2 = TabGroup.Id("2")
    val t1 = tab(id = Tab.Id("1"), groupId = Some(tgId1))
    val t2 = tab(id = Tab.Id("2"), groupId = Some(tgId2))
    val updatedGroup1 = tabGroup(id = tgId1, title = fdr1.title)
    val updatedGroup2 = tabGroup(id = tgId2, title = fdr2.title)
    val bookmarks = MockBookmarks(
      getChildrenResponses = mutable.ListBuffer(
        List(fdr1, fdr2),
        List(bm1),
        List(bm2),
      ),
    )
    val planner = MockSyncPlanner(
      planTabGroupCreationResponses = mutable.ListBuffer(Right(None), Right(None)),
      planTabCreationResponses = mutable.ListBuffer(
        Right(Some(Tabs.CreateTab(url = bm1.url.get, groupId = None))),
        Right(Some(Tabs.CreateTab(url = bm2.url.get, groupId = None))),
      ),
    )
    val tabGroups = MockTabGroups(
      queryResponses = mutable.ListBuffer(List.empty),
      updateResponses = mutable.ListBuffer(updatedGroup1, updatedGroup2),
    )
    val tabs = MockTabs(
      queryResponses = mutable.ListBuffer(List.empty),
      createResponses = mutable.ListBuffer(t1, t2),
      groupResponses = mutable.ListBuffer(tgId1, tgId2),
    )
    val underTest = StartupSync(rootId, bookmarks, planner, tabGroups, tabs)

    // when
    val result = underTest.synchronize()

    // then
    result.map { state =>
      assert(state.foldersByTabGroupId == Map(tgId1 -> fdr1, tgId2 -> fdr2))
      assert(state.bookmarksByTabId == Map(t1.id -> bm1, t2.id -> bm2))
      assert(state.tabGroupsForTabs == Map(t1.id -> tgId1, t2.id -> tgId2))
      assertEquals(
        bookmarks.snapshot,
        Snapshot(
          getChildren = List(
            Bookmarks.GetChildren(rootId),
            Bookmarks.GetChildren(fdr1.id),
            Bookmarks.GetChildren(fdr2.id),
          ),
        ),
      )
      assertEquals(
        tabs.snapshot,
        MockTabs.Snapshot(
          create = List(
            Tabs.CreateTab(url = bm1.url.get, groupId = None),
            Tabs.CreateTab(url = bm2.url.get, groupId = None),
          ),
          group = List(
            Tabs.GroupTabs(List(t1.id), None),
            Tabs.GroupTabs(List(t2.id), None),
          ),
        ),
      )
      assertEquals(
        tabGroups.snapshot,
        MockTabGroups.Snapshot(
          update = List(
            TabGroups.UpdateTabGroup(tgId1, fdr1.title),
            TabGroups.UpdateTabGroup(tgId2, fdr2.title),
          ),
        ),
      )
    }
  }

  test("full round-trip with real SyncPlanner skips both directions when correlated") {
    // given
    val fdr = folder()
    val bm = bookmark(parentId = fdr.id)
    val tgId = TabGroup.Id("1")
    val t = tab(id = Tab.Id("1"), groupId = Some(tgId), url = bm.url)

    val bookmarks = MockBookmarks(
      getChildrenResponses = mutable.ListBuffer(List(fdr), List(bm)),
    )
    val planner = SyncPlanner(rootId)
    val tabGroups = MockTabGroups(
      queryResponses = mutable.ListBuffer(
        List(TabGroup(id = tgId, title = fdr.title)),
      ),
    )
    val tabs = MockTabs(
      queryResponses = mutable.ListBuffer(List(t)),
    )
    val underTest = StartupSync(rootId, bookmarks, planner, tabGroups, tabs)

    // when
    val result = underTest.synchronize()

    // then
    result.map { state =>
      assert(state.foldersByTabGroupId == Map(tgId -> fdr))
      assert(state.bookmarksByTabId == Map(t.id -> bm))
      assert(state.tabGroupsForTabs == Map(t.id -> tgId))
      assertEquals(
        bookmarks.snapshot,
        MockBookmarks.Snapshot(
          getChildren = List(
            Bookmarks.GetChildren(rootId),
            Bookmarks.GetChildren(fdr.id),
          ),
        ),
      )
      assertEquals(tabs.snapshot, MockTabs.Snapshot())
      assertEquals(tabGroups.snapshot, MockTabGroups.Snapshot())
    }
  }

}
