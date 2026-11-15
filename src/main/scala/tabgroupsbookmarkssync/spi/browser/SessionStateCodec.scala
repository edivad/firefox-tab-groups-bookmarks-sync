package tabgroupsbookmarkssync.spi.browser

import tabgroupsbookmarkssync.model.{BookmarkNode, SessionState, State, Tab, TabGroup}
import tabgroupsbookmarkssync.spi.browser.JsDecoder.DecoderError

import scala.scalajs.js
import scala.util.Try

/**
 * Converts the persisted association state to and from a structured-clonable object.
 *
 * Optional titles and URLs are omitted when absent, and identifiers are stored as strings because
 * JavaScript object keys cannot be numbers as large as tab ids.
 */
object SessionStateCodec {

  final val Version = 1

  def encode(session: SessionState): js.Any = {
    val state = session.state
    js.Dictionary[js.Any](
      "version" -> Version,
      "root" -> session.root.value,
      "folders" -> js.Array(state.foldersByTabGroupId.toList.map(encodeFolder)*),
      "bookmarks" -> js.Array(state.bookmarksByTabId.toList.map(encodeBookmark)*),
      "tabs" -> js.Array(state.tabGroupsForTabs.toList.map(encodeTabGroup)*),
    )
  }

  def decode(value: js.Any): Either[DecoderError, SessionState] =
    for {
      fields <- asObject(value, "session")
      version <- numberField(fields, "version").map(_.toInt)
      _ <- Either.cond(
        version == Version,
        (),
        DecoderError.InvalidSession(s"unsupported version $version"),
      )
      root <- stringField(fields, "root").map(BookmarkNode.Id(_))
      folders <- decodeMap(fields, "folders", decodeFolder)
      bookmarks <- decodeMap(fields, "bookmarks", decodeBookmark)
      tabs <- decodeMap(fields, "tabs", decodeTabGroup)
    } yield SessionState(root, State(folders, bookmarks, tabs))

  private def encodeFolder(entry: (TabGroup.Id, BookmarkNode.Folder)): js.Dictionary[js.Any] = {
    val (groupId, folder) = entry
    withOptional(
      fields = js.Dictionary[js.Any](
        "group" -> groupId.value.toString,
        "id" -> folder.id.value,
        "parentId" -> folder.parentId.value,
      ),
      entry = "title" -> folder.title,
    )
  }

  private def encodeBookmark(entry: (Tab.Id, BookmarkNode.Bookmark)): js.Dictionary[js.Any] = {
    val (tabId, bookmark) = entry
    val fields = js.Dictionary[js.Any](
      "tab" -> tabId.value.toString,
      "id" -> bookmark.id.value,
      "parentId" -> bookmark.parentId.value,
    )
    withOptional(
      fields = withOptional(fields = fields, entry = "title" -> bookmark.title),
      entry = "url" -> bookmark.url,
    )
  }

  private def encodeTabGroup(entry: (Tab.Id, TabGroup.Id)): js.Dictionary[js.Any] = {
    val (tabId, groupId) = entry
    js.Dictionary[js.Any](
      "tab" -> tabId.value.toString,
      "group" -> groupId.value.toString,
    )
  }

  private def withOptional(
    fields: js.Dictionary[js.Any],
    entry: (String, Option[String]),
  ): js.Dictionary[js.Any] = {
    val (name, value) = entry
    value.foreach(fields.update(name, _))
    fields
  }

  private def decodeMap[K, V](
    fields: js.Dictionary[js.Any],
    name: String,
    decodeEntry: js.Dictionary[js.Any] => Either[DecoderError, (K, V)],
  ): Either[DecoderError, Map[K, V]] =
    arrayField(fields, name).flatMap { values =>
      values
        .foldLeft[Either[DecoderError, List[(K, V)]]](Right(Nil)) { (acc, value) =>
          for {
            entries <- acc
            entryFields <- asObject(value, s"$name[]")
            entry <- decodeEntry(entryFields)
          } yield entry :: entries
        }
        .map(_.toMap)
    }

  private def decodeFolder(
    fields: js.Dictionary[js.Any],
  ): Either[DecoderError, (TabGroup.Id, BookmarkNode.Folder)] =
    for {
      groupId <- bigIntField(fields, "group", TabGroup.Id(_))
      id <- stringField(fields, "id").map(BookmarkNode.Id(_))
      parentId <- stringField(fields, "parentId").map(BookmarkNode.Id(_))
      title <- optionalStringField(fields, "title")
    } yield (groupId, BookmarkNode.Folder(id = id, parentId = parentId, title = title))

  private def decodeBookmark(
    fields: js.Dictionary[js.Any],
  ): Either[DecoderError, (Tab.Id, BookmarkNode.Bookmark)] =
    for {
      tabId <- bigIntField(fields, "tab", Tab.Id(_))
      id <- stringField(fields, "id").map(BookmarkNode.Id(_))
      parentId <- stringField(fields, "parentId").map(BookmarkNode.Id(_))
      title <- optionalStringField(fields, "title")
      url <- optionalStringField(fields, "url")
    } yield (tabId, BookmarkNode.Bookmark(id = id, parentId = parentId, title = title, url = url))

  private def decodeTabGroup(
    fields: js.Dictionary[js.Any],
  ): Either[DecoderError, (Tab.Id, TabGroup.Id)] =
    for {
      tabId <- bigIntField(fields, "tab", Tab.Id(_))
      groupId <- bigIntField(fields, "group", TabGroup.Id(_))
    } yield (tabId, groupId)

  private def bigIntField[A](
    fields: js.Dictionary[js.Any],
    name: String,
    make: String => A,
  ): Either[DecoderError, A] =
    stringField(fields, name).flatMap { raw =>
      Try(make(raw)).toEither.left.map(e =>
        DecoderError.InvalidSession(s"$name is not a valid identifier: ${e.getMessage}"),
      )
    }

  private def asObject(value: js.Any, name: String): Either[DecoderError, js.Dictionary[js.Any]] =
    if value == null || js.typeOf(value) != "object" || js.Array.isArray(value) then {
      Left(DecoderError.InvalidSession(s"$name is not an object"))
    } else {
      Right(value.asInstanceOf[js.Dictionary[js.Any]])
    }

  private def field(
    fields: js.Dictionary[js.Any],
    name: String,
  ): Either[DecoderError, js.Any] =
    fields
      .get(name)
      .filterNot(value => value == null || js.typeOf(value) == "undefined")
      .toRight(DecoderError.InvalidSession(s"missing $name"))

  private def stringField(
    fields: js.Dictionary[js.Any],
    name: String,
  ): Either[DecoderError, String] =
    field(fields, name).flatMap { value =>
      if js.typeOf(value) == "string" then Right(value.asInstanceOf[String])
      else Left(DecoderError.InvalidSession(s"$name is not a string"))
    }

  private def optionalStringField(
    fields: js.Dictionary[js.Any],
    name: String,
  ): Either[DecoderError, Option[String]] =
    fields.get(name).filterNot(value => value == null || js.typeOf(value) == "undefined") match {
      case None => Right(None)
      case Some(value) if js.typeOf(value) == "string" =>
        Right(Option(value.asInstanceOf[String]).filterNot(_.isBlank))
      case Some(_) => Left(DecoderError.InvalidSession(s"$name is not a string"))
    }

  private def numberField(
    fields: js.Dictionary[js.Any],
    name: String,
  ): Either[DecoderError, Double] =
    field(fields, name).flatMap { value =>
      if js.typeOf(value) == "number" then Right(value.asInstanceOf[Double])
      else Left(DecoderError.InvalidSession(s"$name is not a number"))
    }

  private def arrayField(
    fields: js.Dictionary[js.Any],
    name: String,
  ): Either[DecoderError, js.Array[js.Any]] =
    field(fields, name).flatMap { value =>
      if js.Array.isArray(value) then Right(value.asInstanceOf[js.Array[js.Any]])
      else Left(DecoderError.InvalidSession(s"$name is not an array"))
    }

}
