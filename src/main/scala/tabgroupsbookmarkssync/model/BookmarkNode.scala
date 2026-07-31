package tabgroupsbookmarkssync.model

sealed trait BookmarkNode

object BookmarkNode {

  opaque type Id = String

  object Id {
    def apply(s: String): Id = s

    extension (id: Id) def value: String = id
  }

  final case class Bookmark(
    id: Id,
    parentId: Id,
    title: Option[String],
    url: Option[String],
  ) extends BookmarkNode

  final case class Folder(id: Id, parentId: Id, title: Option[String]) extends BookmarkNode

}
