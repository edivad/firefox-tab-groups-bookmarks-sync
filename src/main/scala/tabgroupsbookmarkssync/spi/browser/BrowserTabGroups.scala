package tabgroupsbookmarkssync.spi.browser

import tabgroupsbookmarkssync.log.{Log, Source}
import tabgroupsbookmarkssync.model.TabGroup as ScalaTabGroup
import tabgroupsbookmarkssync.spi.TabGroups

import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js

class BrowserTabGroups(using ExecutionContext) extends TabGroups {

  override def onTabGroupCreate(callback: ScalaTabGroup => Unit): Unit =
    TabGroupsFacade.onCreated.addListener(handle(callback, _))

  override def onTabGroupUpdate(callback: ScalaTabGroup => Unit): Unit =
    TabGroupsFacade.onUpdated.addListener(handle(callback, _))

  override def onTabGroupRemove(callback: ScalaTabGroup => Unit): Unit =
    TabGroupsFacade.onRemoved.addListener(handle(callback, _))

  override def query(): Future[List[ScalaTabGroup]] =
    TabGroupsFacade
      .query(new js.Object)
      .toFuture
      .map(_.toList.flatMap { tg =>
        tg.decode match {
          case Left(e) =>
            Log(Source.TabGroups, s"Failed to decode tab group: $e")
            Nil
          case Right(v) => v :: Nil
        }
      })

  private def handle(callback: ScalaTabGroup => Unit, tg: TabGroup): Unit =
    tg.decode match {
      case Left(value) => Log(Source.TabGroups, s"Failed to decode tab group: $value")
      case Right(value) => callback(value)
    }

  override def update(request: TabGroups.UpdateTabGroup): Future[ScalaTabGroup] = {
    val updateProperties = js.Dynamic.literal()
    request.title.foreach(updateProperties.title = _)
    TabGroupsFacade.update(request.id.encode, updateProperties).toFuture.flatMap { tg =>
      tg.decode match {
        case Left(e) => Future.failed(IllegalStateException(s"Cannot decode tab group: $e"))
        case Right(v) => Future.successful(v)
      }
    }
  }

}
