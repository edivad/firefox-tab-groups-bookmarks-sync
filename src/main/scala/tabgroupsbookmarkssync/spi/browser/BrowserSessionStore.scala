package tabgroupsbookmarkssync.spi.browser

import tabgroupsbookmarkssync.log.{Log, Source}
import tabgroupsbookmarkssync.model.SessionState
import tabgroupsbookmarkssync.spi.SessionStore

import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js

class BrowserSessionStore(using ExecutionContext) extends SessionStore {

  override def load: Future[Option[SessionState]] =
    SessionStorageFacade
      .get(BrowserSessionStore.Key)
      .toFuture
      .flatMap { items =>
        items.get(BrowserSessionStore.Key) match {
          case None => Future.successful(None)
          case Some(stored) =>
            SessionStateCodec.decode(stored) match {
              case Right(session) => Future.successful(Some(session))
              case Left(error) =>
                Log
                  .future(Source.Storage, s"Discarding unreadable session state: $error")
                  .flatMap(_ => clear())
                  .map(_ => None)
            }
        }
      }

  override def save(session: SessionState): Future[Unit] =
    SessionStorageFacade
      .set(js.Dictionary[js.Any](BrowserSessionStore.Key -> SessionStateCodec.encode(session)))
      .toFuture

  override def clear(): Future[Unit] =
    SessionStorageFacade.remove(BrowserSessionStore.Key).toFuture

}

object BrowserSessionStore {

  final val Key = "syncSession"

}
