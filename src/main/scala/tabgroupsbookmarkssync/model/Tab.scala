package tabgroupsbookmarkssync.model

final case class Tab(
  id: Tab.Id,
  groupId: Option[TabGroup.Id],
  url: Option[String],
  title: Option[String],
)

object Tab {
  opaque type Id = BigInt

  object Id {
    def apply(s: String): Id = BigInt(s)

    extension (id: Id) def value: BigInt = id
  }

}
