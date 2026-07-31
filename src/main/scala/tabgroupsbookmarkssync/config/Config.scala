package tabgroupsbookmarkssync.config

import tabgroupsbookmarkssync.model.BookmarkNode

case class Config(
  syncFolderName: String,
  otherBookmarksId: BookmarkNode.Id,
)

object Config {

  val default: Config = Config(
    syncFolderName = "Tab Group Sync",
    otherBookmarksId = BookmarkNode.Id("unfiled_____"),
  )

}
