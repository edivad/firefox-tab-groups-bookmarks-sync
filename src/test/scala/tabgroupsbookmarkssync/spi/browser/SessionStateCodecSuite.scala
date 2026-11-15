package tabgroupsbookmarkssync.spi.browser

import org.scalacheck.Prop.forAll
import tabgroupsbookmarkssync.model.{BookmarkNode, SessionState, State}
import util.Generators.*

import scala.scalajs.js

class SessionStateCodecSuite extends munit.ScalaCheckSuite {

  property("round-trips any session state") = forAll(sessionState) { session =>
    assertEquals(SessionStateCodec.decode(SessionStateCodec.encode(session)), Right(session))
  }

  property("round-trips an empty session state") = {
    val session = SessionState(BookmarkNode.Id("root_____"), State.empty)
    assertEquals(SessionStateCodec.decode(SessionStateCodec.encode(session)), Right(session))
  }

  test("rejects a value that is not an object") {
    assert(SessionStateCodec.decode("invalid").isLeft)
  }

  test("rejects an unsupported version") {
    // given
    val encoded = SessionStateCodec
      .encode(SessionState(BookmarkNode.Id("root_____"), State.empty))
      .asInstanceOf[js.Dictionary[js.Any]]
    encoded.update("version", SessionStateCodec.Version + 1)

    // when
    val result = SessionStateCodec.decode(encoded)

    // then
    assert(result.isLeft)
  }

  test("rejects a session without a root") {
    // given
    val encoded = js.Dictionary[js.Any](
      "version" -> SessionStateCodec.Version,
      "folders" -> js.Array[js.Any](),
      "bookmarks" -> js.Array[js.Any](),
      "tabs" -> js.Array[js.Any](),
    )

    // when
    val result = SessionStateCodec.decode(encoded)

    // then
    assert(result.isLeft)
  }

  test("rejects a folder with an invalid group id") {
    // given
    val encoded = js.Dictionary[js.Any](
      "version" -> SessionStateCodec.Version,
      "root" -> "root_____",
      "folders" -> js.Array[js.Any](
        js.Dictionary[js.Any](
          "group" -> "not-a-number",
          "id" -> "folder____",
          "parentId" -> "root_____",
        ),
      ),
      "bookmarks" -> js.Array[js.Any](),
      "tabs" -> js.Array[js.Any](),
    )

    // when
    val result = SessionStateCodec.decode(encoded)

    // then
    assert(result.isLeft)
  }

  test("rejects a folder entry that is not an object") {
    // given
    val encoded = js.Dictionary[js.Any](
      "version" -> SessionStateCodec.Version,
      "root" -> "root_____",
      "folders" -> js.Array[js.Any](42),
      "bookmarks" -> js.Array[js.Any](),
      "tabs" -> js.Array[js.Any](),
    )

    // when
    val result = SessionStateCodec.decode(encoded)

    // then
    assert(result.isLeft)
  }

}
