package tabgroupsbookmarkssync.spi.browser

import tabgroupsbookmarkssync.model.BookmarkNode
import tabgroupsbookmarkssync.model.{Tab as ScalaTab, TabGroup as ScalaTabGroup}
import tabgroupsbookmarkssync.spi.browser.JsDecoder.DecoderError

import scala.math.BigDecimal
import scala.scalajs.js

object JsDecoder {

  enum DecoderError {
    case UnsupportedType(s: String)
    case MissingId
    case InvalidNumber
  }

}

trait JsDecoder[A, B] {
  import JsDecoder.DecoderError

  extension (a: A) {
    def decode: Either[DecoderError, B]
  }

}

given nonBlankStringDecoder: JsDecoder[String, Option[String]] with {

  extension (a: String) {

    override def decode: Either[DecoderError, Option[String]] = Right(
      Option(a).filterNot(_.isBlank),
    )

  }

}

given JsDecoder[Option[String], Option[String]] with {

  extension (a: Option[String]) {

    override def decode: Either[DecoderError, Option[String]] = a match {
      case None => Right(None)
      case Some(s) => nonBlankStringDecoder.decode(s)
    }

  }

}

given stringIdDecoder: JsDecoder[String, BookmarkNode.Id] with {

  extension (a: String) {
    override def decode: Either[DecoderError, BookmarkNode.Id] = Right(BookmarkNode.Id(a))
  }

}

given JsDecoder[BookmarkTreeNode, BookmarkNode] with {

  extension (a: BookmarkTreeNode) {

    override def decode: Either[DecoderError, BookmarkNode] = {
      for {
        id <- stringIdDecoder.decode(a.id)
        parentId <- stringIdDecoder.decode(a.parentId)
        title <- nonBlankStringDecoder.decode(a.title)
        result <- a.`type` match {
          case "bookmark" =>
            Right(
              BookmarkNode.Bookmark(
                id = id,
                parentId = parentId,
                title = title,
                url = a.url.toOption,
              ),
            )
          case "folder" =>
            Right(
              BookmarkNode.Folder(
                id = id,
                parentId = parentId,
                title = title,
              ),
            )
          case _ => Left(DecoderError.UnsupportedType(s = a.`type`))
        }
      } yield result
    }

  }

}

given tabGroupIdDecoder: JsDecoder[Double, ScalaTabGroup.Id] with {

  extension (a: Double) {

    override def decode: Either[DecoderError, ScalaTabGroup.Id] =
      try Right(ScalaTabGroup.Id(BigDecimal(a).toBigInt.toString))
      catch case _ => Left(DecoderError.InvalidNumber)

  }

}

given JsDecoder[TabGroup, ScalaTabGroup] with {

  extension (a: TabGroup) {

    override def decode: Either[DecoderError, ScalaTabGroup] =
      for {
        id <- tabGroupIdDecoder.decode(a.id)
        title <- nonBlankStringDecoder.decode(a.title)
      } yield ScalaTabGroup(id = id, title = title)

  }

}

given tabIdDecoder: JsDecoder[Double, ScalaTab.Id] with {

  extension (a: Double) {

    override def decode: Either[DecoderError, ScalaTab.Id] =
      try Right(ScalaTab.Id(BigDecimal(a).toBigInt.toString))
      catch case _ => Left(DecoderError.InvalidNumber)

  }

}

given JsDecoder[Tab, ScalaTab] with {

  extension (a: Tab) {

    override def decode: Either[DecoderError, ScalaTab] =
      for {
        rawId <- a.id.toRight(DecoderError.MissingId)
        id <- tabIdDecoder.decode(rawId)
        maybeGroupId = a.groupId.toOption.filterNot(_ == -1.0)
        groupId <- maybeGroupId match {
          case Some(value) => tabGroupIdDecoder.decode(value).map(Some(_))
          case None => Right(None)
        }
        url <- a.url.toOption.decode
        title <- a.title.toOption.decode
      } yield ScalaTab(id = id, groupId = groupId, url = url, title = title)

  }

}
