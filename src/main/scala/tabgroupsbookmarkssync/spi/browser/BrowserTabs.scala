package tabgroupsbookmarkssync.spi.browser

import tabgroupsbookmarkssync.log.{Log, Source}
import tabgroupsbookmarkssync.model.{Tab as ScalaTab, TabGroup}
import tabgroupsbookmarkssync.spi.Tabs

import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js

class BrowserTabs(using ExecutionContext) extends Tabs {

  override def onTabUpdate(callback: ScalaTab => Unit): Unit =
    TabsFacade.onUpdated.addListener((_, _, t) => handle(callback, t))

  override def query(): Future[List[ScalaTab]] =
    TabsFacade
      .query(new js.Object)
      .toFuture
      .map(_.toList.flatMap { t =>
        t.decode match {
          case Left(e) =>
            Log(Source.Tabs, s"Failed to decode tab: $e")
            Nil
          case Right(v) => v :: Nil
        }
      })

  private def handle(callback: ScalaTab => Unit, t: Tab): Unit =
    t.decode match {
      case Left(value) => Log(Source.Tabs, s"Failed to decode tab: $value")
      case Right(value) => callback(value)
    }

  override def create(request: Tabs.CreateTab): Future[ScalaTab] = {
    val details = js.Dynamic.literal(url = request.url)
    TabsFacade.create(details).toFuture.flatMap { t =>
      t.decode match {
        case Left(e) => Future.failed(IllegalStateException(s"Cannot decode tab: $e"))
        case Right(v) => Future.successful(v)
      }
    }
  }

  override def group(request: Tabs.GroupTabs): Future[TabGroup.Id] = {
    val tabIds = js.Array(request.ids.map(_.encode.toInt)*)
    val options = js.Dynamic.literal(tabIds = tabIds)
    request.groupId.foreach(id => options.groupId = id.encode)
    TabsFacade.group(options).toFuture.flatMap { groupId =>
      tabGroupIdDecoder.decode(groupId) match {
        case Left(e) => Future.failed(IllegalStateException(s"Cannot decode tab group id: $e"))
        case Right(v) => Future.successful(v)
      }
    }
  }

}
