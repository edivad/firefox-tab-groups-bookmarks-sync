package tabgroupsbookmarkssync.spi

import tabgroupsbookmarkssync.model.*

import scala.concurrent.Future

trait Bookmarks {
  def getChildren(request: Bookmarks.GetChildren): Future[List[BookmarkNode]]
  def createFolder(request: Bookmarks.CreateFolder): Future[BookmarkNode.Folder]
  def updateFolder(request: Bookmarks.UpdateFolder): Future[BookmarkNode.Folder]
  def removeFolder(request: Bookmarks.RemoveFolder): Future[Unit]
  def createBookmark(request: Bookmarks.CreateBookmark): Future[BookmarkNode.Bookmark]
  def removeBookmark(request: Bookmarks.RemoveBookmark): Future[Unit]
}

object Bookmarks {
  final case class GetChildren(id: BookmarkNode.Id)
  final case class CreateFolder(parentId: BookmarkNode.Id, title: Option[String])
  final case class UpdateFolder(id: BookmarkNode.Id, title: Option[String])
  final case class RemoveFolder(id: BookmarkNode.Id)
  final case class CreateBookmark(parentId: BookmarkNode.Id, title: Option[String], url: String)
  final case class RemoveBookmark(id: BookmarkNode.Id)
}
