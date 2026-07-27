package tabgroupsbookmarkssync.model

final case class TabGroup(id: TabGroup.Id, title: Option[String])

object TabGroup {
  opaque type Id = BigInt

  object Id {
    def apply(s: String): Id = BigInt(s)

    extension (id: Id) def value: BigInt = id
  }

}
