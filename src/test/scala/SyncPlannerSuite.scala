import org.scalacheck.Prop.forAll
import tabgroupsbookmarkssync.model.{BookmarkNode, Tab, TabGroup}
import tabgroupsbookmarkssync.service.{SyncError, SyncPlanner}
import tabgroupsbookmarkssync.spi.{Bookmarks, Tabs}
import util.Fixtures.newState
import util.Generators.*

class SyncPlannerSuite extends munit.ScalaCheckSuite {

  property("cannot plan folder creation when folder exists") =
    forAll(bookmarkNodeId, folder, tabGroup) { (rootId, folder, tabGroup) =>
      // given
      val state = newState(foldersByTabGroupId = Map(tabGroup.id -> folder))
      val underTest = SyncPlanner(rootId)

      // when
      val result = underTest.planFolderCreation(state, tabGroup)

      // then
      assertEquals(result, Right(None))
    }

  property("can plan folder creation when folder does not exist") =
    forAll(bookmarkNodeId, tabGroup) { (rootId, tabGroup) =>
      // given
      val state = newState()
      val underTest = SyncPlanner(rootId)

      // when
      val result = underTest.planFolderCreation(state, tabGroup)

      // then
      assertEquals(
        result,
        Right[SyncError, Option[Bookmarks.CreateFolder]](
          Some(Bookmarks.CreateFolder(parentId = rootId, title = tabGroup.title)),
        ),
      )
    }

  property("cannot plan folder update when folder does not exist") =
    forAll(bookmarkNodeId, tabGroup) { (rootId, tabGroup) =>
      // given
      val state = newState()
      val underTest = SyncPlanner(rootId)

      // when
      val result = underTest.planTabGroupUpdate(state, tabGroup)

      // then
      assertEquals(result, Left(SyncError.FolderNotFoundForTabGroup(tabGroup)))
    }

  property("can plan folder update when folder already exists") =
    forAll(bookmarkNodeId, folder, tabGroup) { (rootId, folder, tabGroup) =>
      // given
      val state = newState(foldersByTabGroupId = Map(tabGroup.id -> folder))
      val underTest = SyncPlanner(rootId)

      // when
      val result = underTest.planTabGroupUpdate(state, tabGroup)

      // then
      assertEquals(
        result,
        Right[SyncError, Bookmarks.UpdateFolder](
          Bookmarks.UpdateFolder(folder.id, tabGroup.title),
        ),
      )
    }

  property("cannot plan folder removal when folder does not exist") =
    forAll(bookmarkNodeId, tabGroup) { (rootId, tabGroup) =>
      // given
      val state = newState()
      val underTest = SyncPlanner(rootId)

      // when
      val result = underTest.planFolderRemoval(state, tabGroup)

      // then
      assertEquals(result, Left(SyncError.FolderNotFoundForTabGroup(tabGroup)))
    }

  property("can plan folder removal when folder exists") =
    forAll(bookmarkNodeId, folder, tabGroup) { (rootId, folder, tabGroup) =>
      // given
      val state = newState(foldersByTabGroupId = Map(tabGroup.id -> folder))
      val underTest = SyncPlanner(rootId)

      // when
      val result = underTest.planFolderRemoval(state, tabGroup)

      // then
      assertEquals(
        result,
        Right[SyncError, Bookmarks.RemoveFolder](Bookmarks.RemoveFolder(folder.id)),
      )
    }

  property("cannot plan bookmark creation when folder does not exist") =
    forAll(bookmarkNodeId, tabWithUrl, tabGroup) { (rootId, tab, tabGroup) =>
      // given
      val state = newState()
      val underTest = SyncPlanner(rootId)

      // when
      val result = underTest.planBookmarkCreation(state, tab, tabGroup.id)

      // then
      assertEquals(result, Left(SyncError.FolderNotFoundForTabGroupId(tabGroup.id)))
    }

  property("cannot plan bookmark creation when tab's URL is missing") =
    forAll(bookmarkNodeId, folder, tabWithNoUrl, tabGroup) { (rootId, folder, tab, tabGroup) =>
      // given
      val state = newState(foldersByTabGroupId = Map(tabGroup.id -> folder))
      val underTest = SyncPlanner(rootId)

      // when
      val result = underTest.planBookmarkCreation(state, tab, tabGroup.id)

      // then
      assertEquals(result, Left(SyncError.MissingUrlForTab(tab)))
    }

  property("can plan bookmark creation") = forAll(bookmarkNodeId, folder, tabWithUrl, tabGroup) {
    (rootId, folder, tab, tabGroup) =>
      // given
      val state = newState(foldersByTabGroupId = Map(tabGroup.id -> folder))
      val underTest = SyncPlanner(rootId)

      // when
      val result = underTest.planBookmarkCreation(state, tab, tabGroup.id)

      // then
      assertEquals(
        result,
        Right[SyncError, Option[Bookmarks.CreateBookmark]](
          Some(
            Bookmarks.CreateBookmark(
              parentId = folder.id,
              title = tab.title,
              url = tab.url.getOrElse(""),
            ),
          ),
        ),
      )
  }

  property("skips bookmark creation when bookmark already exists for tab") =
    forAll(bookmarkNodeId, folder, tabWithUrl, tabGroup) { (rootId, folder, tab, tabGroup) =>
      // given
      val bm = BookmarkNode.Bookmark(
        id = BookmarkNode.Id("id"),
        parentId = folder.id,
        title = tab.title,
        url = tab.url,
      )
      val state = newState(
        foldersByTabGroupId = Map(tabGroup.id -> folder),
        bookmarksByTabId = Map(tab.id -> bm),
        tabGroupsForTabs = Map(tab.id -> tabGroup.id),
      )
      val underTest = SyncPlanner(rootId)

      // when
      val result = underTest.planBookmarkCreation(state, tab, tabGroup.id)

      // then
      assertEquals(result, Right(None))
    }

  property("creates bookmark when tab has bookmark in a different group") =
    forAll(bookmarkNodeId, folder, tabWithUrl, tabGroup) { (rootId, folder, tab, tabGroup) =>
      // given
      val otherGroupId = TabGroup.Id("999")
      val bm = BookmarkNode.Bookmark(
        id = BookmarkNode.Id("id"),
        parentId = folder.id,
        title = tab.title,
        url = tab.url,
      )
      val state = newState(
        foldersByTabGroupId = Map(tabGroup.id -> folder),
        bookmarksByTabId = Map(tab.id -> bm),
        tabGroupsForTabs = Map(tab.id -> otherGroupId),
      )
      val underTest = SyncPlanner(rootId)

      // when
      val result = underTest.planBookmarkCreation(state, tab, tabGroup.id)

      // then
      assertEquals(
        result,
        Right[SyncError, Option[Bookmarks.CreateBookmark]](
          Some(
            Bookmarks.CreateBookmark(
              parentId = folder.id,
              title = tab.title,
              url = tab.url.getOrElse(""),
            ),
          ),
        ),
      )
    }

  property("cannot plan bookmark removal when bookmark does not exist") =
    forAll(bookmarkNodeId, tab, bookmarkWithUrl) { (rootId, tab, bookmark) =>
      // given
      val state = newState()
      val underTest = SyncPlanner(rootId)

      // when
      val result = underTest.planBookmarkRemoval(state, tab)

      // then
      assertEquals(result, Left(SyncError.BookmarkNotFoundForTab(tab)))
    }

  property("can plan bookmark removal") = forAll(bookmarkNodeId, tab, bookmarkWithUrl) {
    (rootId, tab, bookmark) =>
      // given
      val state = newState(bookmarksByTabId = Map(tab.id -> bookmark))
      val underTest = SyncPlanner(rootId)

      // when
      val result = underTest.planBookmarkRemoval(state, tab)

      // then
      assertEquals(
        result,
        Right[SyncError, Bookmarks.RemoveBookmark](
          Bookmarks.RemoveBookmark(bookmark.id),
        ),
      )
  }

  property("cannot plan bookmark recreation when URL is missing") =
    forAll(bookmarkNodeId, folder, tabWithNoUrl, tabGroup, bookmarkWithUrl) {
      (rootId, folder, tab, tabGroup, bookmark) =>
        // given — bookmark and folder exist so both pass, then URL check fails
        val state = newState(
          foldersByTabGroupId = Map(tabGroup.id -> folder),
          bookmarksByTabId = Map(tab.id -> bookmark),
        )
        val underTest = SyncPlanner(rootId)

        // when
        val result = underTest.planBookmarkRecreation(state, tab, tabGroup.id)

        // then
        assertEquals(result, Left(SyncError.MissingUrlForTab(tab)))
    }

  property("cannot plan bookmark recreation when folder does not exist") =
    forAll(bookmarkNodeId, tabWithUrl, tabGroup, bookmarkWithUrl) {
      (rootId, tab, tabGroup, bookmark) =>
        // given — bookmark exists so planBookmarkRemoval passes, then folder check fails
        val state = newState(bookmarksByTabId = Map(tab.id -> bookmark))
        val underTest = SyncPlanner(rootId)

        // when
        val result = underTest.planBookmarkRecreation(state, tab, tabGroup.id)

        // then
        assertEquals(result, Left(SyncError.FolderNotFoundForTabGroupId(tabGroup.id)))
    }

  property("cannot plan bookmark recreation when bookmark does not exist") =
    forAll(bookmarkNodeId, folder, tabWithUrl, tabGroup) { (rootId, folder, tab, tabGroup) =>
      // given — bookmark check is first, fails before URL/folder checks
      val state = newState(foldersByTabGroupId = Map(tabGroup.id -> folder))
      val underTest = SyncPlanner(rootId)

      // when
      val result = underTest.planBookmarkRecreation(state, tab, tabGroup.id)

      // then
      assertEquals(result, Left(SyncError.BookmarkNotFoundForTab(tab)))
    }

  property("can plan bookmark recreation") =
    forAll(bookmarkNodeId, folder, tabWithUrl, tabGroup, bookmarkWithUrl) {
      (rootId, folder, tab, tabGroup, bookmark) =>
        // given
        val state = newState(
          foldersByTabGroupId = Map(tabGroup.id -> folder),
          bookmarksByTabId = Map(tab.id -> bookmark),
        )
        val underTest = SyncPlanner(rootId)

        // when
        val result = underTest.planBookmarkRecreation(state, tab, tabGroup.id)

        // then
        assertEquals(
          result,
          Right[SyncError, (Bookmarks.RemoveBookmark, Bookmarks.CreateBookmark)](
            (
              Bookmarks.RemoveBookmark(bookmark.id),
              Bookmarks.CreateBookmark(
                parentId = folder.id,
                title = tab.title,
                url = tab.url.getOrElse(""),
              ),
            ),
          ),
        )
    }

  property("can resolve tab group when title matches exactly one") =
    forAll(bookmarkNodeId, folder, tabGroup) { (rootId, folder, tabGroup) =>
      // given
      val group = tabGroup.copy(title = folder.title)
      val underTest = SyncPlanner(rootId)

      // when
      val result = underTest.planTabGroupCreation(folder, List(group))

      // then
      assertEquals(
        result,
        Right(Some(group.id)),
      )
    }

  property("cannot resolve tab group when no title match") = forAll(bookmarkNodeId, folder) {
    (rootId, folder) =>
      // given
      val underTest = SyncPlanner(rootId)

      // when
      val result = underTest.planTabGroupCreation(folder, List.empty)

      // then
      assertEquals(
        result,
        Right(None),
      )
  }

  property("cannot resolve tab group when title ambiguous") =
    forAll(bookmarkNodeId, folder, tabGroup, tabGroup) { (rootId, folder, group1, group2) =>
      // given
      val group1WithTitle = group1.copy(title = folder.title)
      val group2WithTitle = group2.copy(title = folder.title)
      val underTest = SyncPlanner(rootId)

      // when
      val result = underTest.planTabGroupCreation(folder, List(group1WithTitle, group2WithTitle))

      // then
      assertEquals(
        result,
        Right(None),
      )
    }

  property("can skip tab creation when URL already exists") =
    forAll(bookmarkNodeId, tabWithUrl, bookmarkWithUrl, maybeTabGroupId) {
      (rootId, tab, bookmark, maybeTabGroupId) =>
        // given
        val bookmarkWithTabUrl = bookmark.copy(url = tab.url)
        val underTest = SyncPlanner(rootId)

        // when
        val result = underTest.planTabCreation(bookmarkWithTabUrl, List(tab), maybeTabGroupId)

        // then
        assertEquals(result, Right(None))
    }

  property("can plan tab creation when no matching tab exists") =
    forAll(bookmarkNodeId, bookmarkWithUrl, maybeTabGroupId) {
      (rootId, bookmark, maybeTabGroupId) =>
        // given
        val underTest = SyncPlanner(rootId)

        // when
        val result = underTest.planTabCreation(bookmark, List.empty, maybeTabGroupId)

        // then
        assertEquals(
          result,
          Right(Some(Tabs.CreateTab(url = bookmark.url.get, groupId = maybeTabGroupId))),
        )
    }

  property("can skip tab creation when bookmark has no URL") =
    forAll(bookmarkWithNoUrl, maybeTabGroupId) { (bookmark, maybeTabGroupId) =>
      // given
      val underTest = SyncPlanner(BookmarkNode.Id("root"))

      // when
      val result = underTest.planTabCreation(bookmark, List.empty, maybeTabGroupId)

      // then
      assertEquals(result, Right(None))
    }

}
