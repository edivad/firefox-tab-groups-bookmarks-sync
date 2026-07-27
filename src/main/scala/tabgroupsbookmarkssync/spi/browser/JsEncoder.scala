package tabgroupsbookmarkssync.spi.browser

import tabgroupsbookmarkssync.model.{BookmarkNode, Tab, TabGroup}

trait JsEncoder[A, B] {

  extension (a: A) {
    def encode: B
  }

}

given JsEncoder[BookmarkNode.Id, String] with {

  extension (a: BookmarkNode.Id) {
    override def encode: String = a.value
  }

}

given tabGroupIdEncoder: JsEncoder[TabGroup.Id, Double] with {

  extension (a: TabGroup.Id) {
    override def encode: Double = a.value.toDouble
  }

}

given tabIdEncoder: JsEncoder[Tab.Id, Double] with {

  extension (a: Tab.Id) {
    override def encode: Double = a.value.toDouble
  }

}
