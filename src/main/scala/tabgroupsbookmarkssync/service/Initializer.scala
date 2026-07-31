package tabgroupsbookmarkssync.service

import tabgroupsbookmarkssync.config.Config
import tabgroupsbookmarkssync.model.*
import tabgroupsbookmarkssync.spi.Bookmarks

import scala.concurrent.{ExecutionContext, Future}

final class Initializer(config: Config, bookmarks: Bookmarks)(using ExecutionContext) {

  def ensureRootFolderIsPresent: Future[BookmarkNode.Folder] =
    for {
      children <- bookmarks.getChildren(Bookmarks.GetChildren(id = config.otherBookmarksId))
      found = children.collect { case f @ BookmarkNode.Folder(_, _, Some(config.syncFolderName)) =>
        f
      }
      folder <- found match {
        case value :: Nil => Future.successful(value)
        case Nil =>
          bookmarks.createFolder(
            Bookmarks.CreateFolder(
              parentId = config.otherBookmarksId,
              title = Some(config.syncFolderName),
            ),
          )
        case _ => Future.failed(IllegalStateException("Multiple folders found"))
      }
    } yield folder

}
