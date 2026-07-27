import mock.MockBookmarks
import mock.MockBookmarks.Snapshot
import tabgroupsbookmarkssync.model.*
import tabgroupsbookmarkssync.service.Initializer
import tabgroupsbookmarkssync.spi.Bookmarks
import util.Fixtures.{bookmark, config, folder}

import scala.collection.mutable
import scala.concurrent.ExecutionContext

class InitializerSuite extends munit.FunSuite {

  given ec: ExecutionContext = ExecutionContext.global

  test("return existing sync folder when found among children") {
    // given
    val cfg = config()
    val existingFolder =
      folder(parentId = cfg.otherBookmarksId, title = Some(cfg.syncFolderName))
    val bookmarks = MockBookmarks(getChildrenResponses =
      mutable.ListBuffer(
        List(
          bookmark(parentId = cfg.otherBookmarksId),
          existingFolder,
          folder(parentId = cfg.otherBookmarksId),
        ),
      ),
    )
    val underTest = Initializer(cfg, bookmarks)

    // when
    val result = underTest.ensureRootFolderIsPresent

    // then
    result.map { r =>
      assertEquals(r, existingFolder)
      assertEquals(
        bookmarks.snapshot,
        Snapshot(getChildren = List(Bookmarks.GetChildren(id = cfg.otherBookmarksId))),
      )
    }
  }

  test("fail when multiple sync folders found") {
    // given
    val cfg = config()
    val bookmarks = MockBookmarks(getChildrenResponses =
      mutable.ListBuffer(
        List(
          folder(parentId = cfg.otherBookmarksId, title = Some(cfg.syncFolderName)),
          folder(parentId = cfg.otherBookmarksId, title = Some(cfg.syncFolderName)),
        ),
      ),
    )
    val underTest = Initializer(cfg, bookmarks)

    // when
    val result = underTest.ensureRootFolderIsPresent
      .map(_ => fail("expected IllegalStateException"))
      .recover { case e: IllegalStateException => e.getMessage }

    // then
    result.map { message =>
      assertEquals(message, "Multiple folders found")
      assertEquals(
        bookmarks.snapshot,
        Snapshot(getChildren = List(Bookmarks.GetChildren(id = cfg.otherBookmarksId))),
      )
    }
  }

  test("create and return new folder when no sync folder exists among children") {
    // given
    val cfg = config()
    val newFolder = folder(parentId = cfg.otherBookmarksId, title = Some(cfg.syncFolderName))
    val bookmarks = MockBookmarks(
      getChildrenResponses = mutable.ListBuffer(
        List(
          bookmark(parentId = cfg.otherBookmarksId),
          folder(parentId = cfg.otherBookmarksId),
        ),
      ),
      createFolderResponses = mutable.ListBuffer(newFolder),
    )
    val underTest = Initializer(cfg, bookmarks)

    // when
    val result = underTest.ensureRootFolderIsPresent

    // then
    result.map { r =>
      assertEquals(r, newFolder)
      assertEquals(
        bookmarks.snapshot,
        Snapshot(
          getChildren = List(Bookmarks.GetChildren(id = cfg.otherBookmarksId)),
          createFolder = List(
            Bookmarks.CreateFolder(
              parentId = cfg.otherBookmarksId,
              title = Some(cfg.syncFolderName),
            ),
          ),
        ),
      )
    }
  }

  test("create and return new folder when only a bookmark (not folder) has the sync name") {
    // given
    val cfg = config()
    val newFolder = folder(parentId = cfg.otherBookmarksId, title = Some(cfg.syncFolderName))
    val bookmarks = MockBookmarks(
      getChildrenResponses = mutable.ListBuffer(
        List(bookmark(parentId = cfg.otherBookmarksId, title = Some(cfg.syncFolderName))),
      ),
      createFolderResponses = mutable.ListBuffer(newFolder),
    )
    val underTest = Initializer(cfg, bookmarks)

    // when
    val result = underTest.ensureRootFolderIsPresent

    // then
    result.map { r =>
      assertEquals(r, newFolder)
      assertEquals(
        bookmarks.snapshot,
        Snapshot(
          getChildren = List(Bookmarks.GetChildren(id = cfg.otherBookmarksId)),
          createFolder = List(
            Bookmarks.CreateFolder(
              parentId = cfg.otherBookmarksId,
              title = Some(cfg.syncFolderName),
            ),
          ),
        ),
      )
    }
  }

}
