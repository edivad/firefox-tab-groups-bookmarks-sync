package tabgroupsbookmarkssync.spi

import tabgroupsbookmarkssync.model.SessionState

import scala.concurrent.Future

/**
 * Persists the association state for the current browser session.
 *
 * The background page can be unloaded at any time while the browser keeps running. State stored
 * here survives that unload and is dropped when the browser stops, so a restarted browser rebuilds
 * it from the bookmarks.
 */
trait SessionStore {
  def load: Future[Option[SessionState]]
  def save(session: SessionState): Future[Unit]
  def clear(): Future[Unit]
}
