package tabgroupsbookmarkssync.spi.browser

import org.scalacheck.Prop.forAll
import tabgroupsbookmarkssync.model.{BookmarkNode, Tab as ScalaTab, TabGroup as ScalaTabGroup}
import tabgroupsbookmarkssync.spi.browser.JsDecoder.DecoderError
import util.Generators.*

import scala.scalajs.js

class JsDecoderSuite extends munit.ScalaCheckSuite {

  property("returns Some for any non-blank string") = forAll(nonBlankString) { s =>
    assertEquals(nonBlankStringDecoder.decode(s), Right(Some(s)))
  }

  property("returns None for any blank string") = forAll(blankString) { s =>
    assertEquals(nonBlankStringDecoder.decode(s), Right(None))
  }

  property("decodes Option[String] as Some for any string value") = forAll(anyString) { s =>
    assertEquals(Option(s).decode, nonBlankStringDecoder.decode(s))
  }

  property("Option[String] passes None through") =
    assertEquals((None: Option[String]).decode, Right(None))

  property("decodes string to BookmarkNode.Id") = forAll(anyString) { s =>
    assertEquals(stringIdDecoder.decode(s), Right(BookmarkNode.Id(s)))
  }

  property("decodes integer value to TabGroup.Id") = forAll(jsNumber) { n =>
    assertEquals(tabGroupIdDecoder.decode(n), Right(ScalaTabGroup.Id(n.toLong.toString)))
  }

  property("decodes integer value to Tab.Id") = forAll(jsNumber) { n =>
    assertEquals(tabIdDecoder.decode(n), Right(ScalaTab.Id(n.toLong.toString)))
  }

  property("decodes bookmark type") = forAll(anyString, anyString, anyString, anyString) {
    (rawId, rawParentId, rawTitle, rawUrl) =>
      // given
      val node = js.Dynamic
        .literal(
          id = rawId,
          parentId = rawParentId,
          title = rawTitle,
          `type` = "bookmark",
          url = rawUrl,
        )
        .asInstanceOf[BookmarkTreeNode]

      // when
      val result = node.decode

      // then
      assertEquals(
        result,
        Right(
          BookmarkNode.Bookmark(
            id = BookmarkNode.Id(rawId),
            parentId = BookmarkNode.Id(rawParentId),
            title = nonBlankStringDecoder.decode(rawTitle).toOption.flatten,
            url = Some(rawUrl),
          ),
        ),
      )
  }

  property("decodes folder type") = forAll(anyString, anyString, anyString) {
    (rawId, rawParentId, rawTitle) =>
      // given
      val node = js.Dynamic
        .literal(id = rawId, parentId = rawParentId, title = rawTitle, `type` = "folder")
        .asInstanceOf[BookmarkTreeNode]

      // when
      val result = node.decode

      // then
      assertEquals(
        result,
        Right(
          BookmarkNode.Folder(
            id = BookmarkNode.Id(rawId),
            parentId = BookmarkNode.Id(rawParentId),
            title = nonBlankStringDecoder.decode(rawTitle).toOption.flatten,
          ),
        ),
      )
  }

  property("fails on unsupported type") = forAll(anyString) { rawType =>
    // given
    val node = js.Dynamic
      .literal(id = "x", parentId = "p", title = "t", `type` = rawType)
      .asInstanceOf[BookmarkTreeNode]

    // when
    val result = node.decode

    // then
    val isValidType = rawType == "bookmark" || rawType == "folder"
    if isValidType then assert(result.isRight)
    else assertEquals(result, Left(DecoderError.UnsupportedType(rawType)))
  }

  property("decodes tab group") = forAll(jsNumber, anyString) { (rawId, rawTitle) =>
    // given
    val tg = js.Dynamic.literal(id = rawId, title = rawTitle).asInstanceOf[TabGroup]

    // when
    val result = tg.decode

    // then
    assertEquals(
      result,
      Right(
        ScalaTabGroup(
          id = ScalaTabGroup.Id(rawId.toLong.toString),
          title = nonBlankStringDecoder.decode(rawTitle).toOption.flatten,
        ),
      ),
    )
  }

  property("decodes tab") = forAll(jsNumber, jsGroupId, anyString, anyString) {
    (rawId, rawGroupId, rawUrl, rawTitle) =>
      // given
      val t = rawGroupId match {
        case Some(gid) =>
          js.Dynamic
            .literal(id = rawId, url = rawUrl, title = rawTitle, groupId = gid)
            .asInstanceOf[Tab]
        case None =>
          js.Dynamic.literal(id = rawId, url = rawUrl, title = rawTitle).asInstanceOf[Tab]
      }

      // when
      val result = t.decode

      // then
      val expectedGroupId = rawGroupId
        .filterNot(_.toLong.toString == "-1")
        .map(gid => ScalaTabGroup.Id(gid.toLong.toString))
      assertEquals(
        result,
        Right(
          ScalaTab(
            id = ScalaTab.Id(rawId.toLong.toString),
            groupId = expectedGroupId,
            url = nonBlankStringDecoder.decode(rawUrl).toOption.flatten,
            title = nonBlankStringDecoder.decode(rawTitle).toOption.flatten,
          ),
        ),
      )
  }

  property("fails on missing tab id") = forAll(jsGroupId, anyString, anyString) {
    (rawGroupId, rawUrl, rawTitle) =>
      // given
      val t = rawGroupId match {
        case Some(gid) =>
          js.Dynamic.literal(url = rawUrl, title = rawTitle, groupId = gid).asInstanceOf[Tab]
        case None =>
          js.Dynamic.literal(url = rawUrl, title = rawTitle).asInstanceOf[Tab]
      }

      // when
      val result = t.decode

      // then
      assertEquals(result, Left(DecoderError.MissingId))
  }

  property("BookmarkNode.Id encodes to string") = forAll(anyString) { s =>
    import tabgroupsbookmarkssync.spi.browser.given JsEncoder[BookmarkNode.Id, String]
    assertEquals(BookmarkNode.Id(s).encode, s)
  }

}
