package util

import org.scalacheck.Gen
import tabgroupsbookmarkssync.model.{BookmarkNode, Tab, TabGroup}

object Generators {

  val nonBlankString: Gen[String] = for {
    n <- Gen.chooseNum(1, 20)
    s <- Gen.stringOfN(n, Gen.alphaChar)
  } yield s

  val blankString: Gen[String] = Gen.oneOf(Gen.const(""), Gen.const("   "), Gen.const("\t\n"))

  val anyString: Gen[String] = Gen.oneOf(nonBlankString, blankString)

  val jsNumber: Gen[Double] = Gen.chooseNum(0L, 1000000000000L).map(_.toDouble)

  val jsGroupId: Gen[Option[Double]] = Gen.oneOf(Gen.const(None), jsNumber.map(Some(_)))

  val bookmarkNodeId: Gen[BookmarkNode.Id] =
    Gen.stringOfN(10, Gen.alphaChar).map(BookmarkNode.Id(_))

  val tabGroupId: Gen[TabGroup.Id] = Gen.posNum[Long].map(n => TabGroup.Id(n.toString))

  val maybeTabGroupId: Gen[Option[TabGroup.Id]] =
    Gen.oneOf(Gen.const(None), tabGroupId.map(Some(_)))

  val tabId: Gen[Tab.Id] = Gen.posNum[Long].map(n => Tab.Id(n.toString))

  val title: Gen[Option[String]] =
    Gen.oneOf(Gen.const(None), Gen.stringOfN(5, Gen.alphaChar).map(Some(_)))

  val url: Gen[String] = Gen.stringOfN(5, Gen.alphaChar).map(s => s"https://example.com/$s")

  val folder: Gen[BookmarkNode.Folder] = for {
    id <- bookmarkNodeId
    parentId <- bookmarkNodeId
    title <- title
  } yield BookmarkNode.Folder(id = id, parentId = parentId, title = title)

  val tabGroup: Gen[TabGroup] = for {
    id <- tabGroupId
    title <- title
  } yield TabGroup(id = id, title = title)

  val tabWithUrl: Gen[Tab] = for {
    id <- tabId
    groupId <- tabGroupId
    url <- url
    title <- title
  } yield Tab(id = id, groupId = Some(groupId), url = Some(url), title = title)

  val tabWithNoUrl: Gen[Tab] = for {
    id <- tabId
    groupId <- tabGroupId
    title <- title
  } yield Tab(id = id, groupId = Some(groupId), url = None, title = title)

  val tab: Gen[Tab] = Gen.oneOf(tabWithUrl, tabWithNoUrl)

  val bookmarkWithUrl: Gen[BookmarkNode.Bookmark] = for {
    id <- bookmarkNodeId
    parentId <- bookmarkNodeId
    title <- title
    url <- url
  } yield BookmarkNode.Bookmark(id = id, parentId = parentId, title = title, url = Some(url))

  val bookmarkWithNoUrl: Gen[BookmarkNode.Bookmark] = for {
    id <- bookmarkNodeId
    parentId <- bookmarkNodeId
    title <- title
  } yield BookmarkNode.Bookmark(id = id, parentId = parentId, title = title, url = None)

}
