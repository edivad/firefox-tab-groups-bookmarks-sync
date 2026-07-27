package tabgroupsbookmarkssync.spi.browser

import tabgroupsbookmarkssync.model.*
import tabgroupsbookmarkssync.spi.Bookmarks
import tabgroupsbookmarkssync.spi.browser.BrowserBookmarks.*

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag
import scala.scalajs.js
import scala.scalajs.js.Thenable.Implicits.thenable2future

object BrowserBookmarks {

  private def expectType[T <: BookmarkNode: ClassTag](node: BookmarkNode): Future[T] =
    node match {
      case t: T => Future.successful(t)
      case other => Future.failed(IllegalStateException(s"Unexpected element: $other"))
    }

  extension [E, A](self: Either[E, A]) {

    def toFuture(onLeft: E => Throwable): Future[A] = self match {
      case Left(value) => Future.failed(onLeft(value))
      case Right(value) => Future.successful(value)
    }

  }

  private def cannotDecodeElementException(error: JsDecoder.DecoderError) =
    IllegalStateException(s"Cannot decode element: $error")

  private def cannotDecodeElementsException(errors: List[JsDecoder.DecoderError]) =
    IllegalStateException(s"Cannot decode elements: ${errors.mkString(", ")}")

}

class BrowserBookmarks(using ExecutionContext) extends Bookmarks {

  override def getChildren(request: Bookmarks.GetChildren): Future[List[BookmarkNode]] =
    for {
      children <- BookmarksFacade.getChildren(request.id.encode).toFuture
      (errors, elements) = children.toList.map(a => a.decode).partitionMap(identity)
      result <-
        if errors.isEmpty then Future.successful(elements)
        else Future.failed(cannotDecodeElementsException(errors))
    } yield result

  override def createFolder(request: Bookmarks.CreateFolder): Future[BookmarkNode.Folder] = {
    val details = js.Dynamic.literal(
      parentId = request.parentId.encode,
    )
    request.title.foreach(details.title = _)
    for {
      created <- BookmarksFacade.create(details).toFuture
      node <- created.decode.toFuture(cannotDecodeElementException)
      result <- expectType[BookmarkNode.Folder](node)
    } yield result
  }

  override def updateFolder(request: Bookmarks.UpdateFolder): Future[BookmarkNode.Folder] = {
    val Bookmarks.UpdateFolder(id, title) = request
    val change = js.Dynamic.literal()
    title.foreach(change.title = _)
    for {
      updated <- BookmarksFacade.update(id.encode, change).toFuture
      node <- updated.decode.toFuture(cannotDecodeElementException)
      result <- expectType[BookmarkNode.Folder](node)
    } yield result
  }

  override def removeFolder(request: Bookmarks.RemoveFolder): Future[Unit] =
    BookmarksFacade.removeTree(request.id.encode)

  override def createBookmark(request: Bookmarks.CreateBookmark): Future[BookmarkNode.Bookmark] = {
    val details = js.Dynamic.literal(
      parentId = request.parentId.encode,
      url = request.url,
    )
    request.title.foreach(details.title = _)
    for {
      created <- BookmarksFacade.create(details).toFuture
      node <- created.decode.toFuture(cannotDecodeElementException)
      result <- expectType[BookmarkNode.Bookmark](node)
    } yield result
  }

  override def removeBookmark(request: Bookmarks.RemoveBookmark): Future[Unit] =
    BookmarksFacade.remove(request.id.encode)

}
