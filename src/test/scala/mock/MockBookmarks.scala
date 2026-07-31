package mock

import tabgroupsbookmarkssync.model.*
import tabgroupsbookmarkssync.spi.Bookmarks

import scala.collection.mutable
import scala.concurrent.Future

case class MockBookmarks(
  getChildrenRequests: mutable.ListBuffer[Bookmarks.GetChildren] = mutable.ListBuffer.empty,
  getChildrenResponses: mutable.ListBuffer[List[BookmarkNode]] = mutable.ListBuffer.empty,
  createFolderRequests: mutable.ListBuffer[Bookmarks.CreateFolder] = mutable.ListBuffer.empty,
  createFolderResponses: mutable.ListBuffer[BookmarkNode.Folder] = mutable.ListBuffer.empty,
  updateFolderRequests: mutable.ListBuffer[Bookmarks.UpdateFolder] = mutable.ListBuffer.empty,
  updateFolderResponses: mutable.ListBuffer[BookmarkNode.Folder] = mutable.ListBuffer.empty,
  removeFolderRequests: mutable.ListBuffer[Bookmarks.RemoveFolder] = mutable.ListBuffer.empty,
  createBookmarkRequests: mutable.ListBuffer[Bookmarks.CreateBookmark] = mutable.ListBuffer.empty,
  createBookmarkResponses: mutable.ListBuffer[BookmarkNode.Bookmark] = mutable.ListBuffer.empty,
  removeBookmarkRequests: mutable.ListBuffer[Bookmarks.RemoveBookmark] = mutable.ListBuffer.empty,
  removeBookmarkFailures: mutable.ListBuffer[Throwable] = mutable.ListBuffer.empty,
  createBookmarkFailures: mutable.ListBuffer[Throwable] = mutable.ListBuffer.empty,
) extends Bookmarks {

  def snapshot: MockBookmarks.Snapshot = MockBookmarks.Snapshot(
    getChildren = getChildrenRequests.toList,
    createFolder = createFolderRequests.toList,
    updateFolder = updateFolderRequests.toList,
    removeFolder = removeFolderRequests.toList,
    createBookmark = createBookmarkRequests.toList,
    removeBookmark = removeBookmarkRequests.toList,
  )

  override def getChildren(request: Bookmarks.GetChildren): Future[List[BookmarkNode]] =
    Future.successful {
      getChildrenRequests.addOne(request)
      getChildrenResponses.remove(0)
    }

  override def createFolder(request: Bookmarks.CreateFolder): Future[BookmarkNode.Folder] =
    Future.successful {
      createFolderRequests.addOne(request)
      createFolderResponses.remove(0)
    }

  override def updateFolder(request: Bookmarks.UpdateFolder): Future[BookmarkNode.Folder] =
    Future.successful {
      updateFolderRequests.addOne(request)
      updateFolderResponses.remove(0)
    }

  override def removeFolder(request: Bookmarks.RemoveFolder): Future[Unit] =
    Future.successful {
      removeFolderRequests.addOne(request)
    }

  override def createBookmark(
    request: Bookmarks.CreateBookmark,
  ): Future[BookmarkNode.Bookmark] = {
    createBookmarkRequests.addOne(request)
    if createBookmarkFailures.nonEmpty then Future.failed(createBookmarkFailures.remove(0))
    else Future.successful(createBookmarkResponses.remove(0))
  }

  override def removeBookmark(request: Bookmarks.RemoveBookmark): Future[Unit] = {
    removeBookmarkRequests.addOne(request)
    if removeBookmarkFailures.nonEmpty then Future.failed(removeBookmarkFailures.remove(0))
    else Future.successful(())
  }

}

object MockBookmarks {

  val unused: MockBookmarks = MockBookmarks()

  case class Snapshot(
    getChildren: List[Bookmarks.GetChildren] = Nil,
    createFolder: List[Bookmarks.CreateFolder] = Nil,
    updateFolder: List[Bookmarks.UpdateFolder] = Nil,
    removeFolder: List[Bookmarks.RemoveFolder] = Nil,
    createBookmark: List[Bookmarks.CreateBookmark] = Nil,
    removeBookmark: List[Bookmarks.RemoveBookmark] = Nil,
  )

}
