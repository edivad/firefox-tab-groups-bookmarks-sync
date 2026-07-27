package tabgroupsbookmarkssync.spi.browser

import org.scalacheck.Prop.forAll
import tabgroupsbookmarkssync.model.{BookmarkNode, Tab as ScalaTab, TabGroup as ScalaTabGroup}
import util.Generators.*

class JsEncoderSuite extends munit.ScalaCheckSuite {

  property("encodes BookmarkNode.Id to its value string") = forAll(anyString) { s =>
    assertEquals(BookmarkNode.Id(s).encode, s)
  }

  property("encodes TabGroup.Id to its numeric value as Double") = forAll(tabGroupId) { id =>
    assertEquals(id.encode, id.value.toDouble)
  }

  property("encodes Tab.Id to its numeric value as Double") = forAll(tabId) { id =>
    assertEquals(id.encode, id.value.toDouble)
  }

}
