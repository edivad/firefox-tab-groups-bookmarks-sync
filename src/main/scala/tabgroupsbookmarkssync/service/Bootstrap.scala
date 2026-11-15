package tabgroupsbookmarkssync.service

import tabgroupsbookmarkssync.config.Config
import tabgroupsbookmarkssync.log.{Log, Source}
import tabgroupsbookmarkssync.model.{BookmarkNode, SessionState}
import tabgroupsbookmarkssync.spi.{Bookmarks, SessionStore, TabGroups, Tabs}

import scala.concurrent.{ExecutionContext, Future}

/**
 * Determines the association state the extension starts from.
 *
 * A state persisted for the current sync root is reused, so a background page woken by an event
 * keeps the associations established before it was unloaded. Rebuilding them from scratch would
 * match folders and groups by title, which duplicates both when the waking event is itself a
 * rename.
 *
 * Without a usable persisted state (first load, browser restart, discarded snapshot) the bookmarks
 * are reconciled against the existing tab groups.
 */
final class Bootstrap(
  config: Config,
  bookmarks: Bookmarks,
  tabGroups: TabGroups,
  tabs: Tabs,
  store: SessionStore,
)(using ExecutionContext) {

  def resolve(): Future[SessionState] =
    for {
      root <- Initializer(config, bookmarks).ensureRootFolderIsPresent
      restored <- restore(root.id)
      session <- restored match {
        case Some(value) => Future.successful(value)
        case None => reconcile(root.id)
      }
    } yield session

  private def restore(root: BookmarkNode.Id): Future[Option[SessionState]] =
    store.load
      .recover { case e =>
        Log(Source.Bootstrap, s"Cannot load session state: $e")
        None
      }
      .flatMap {
        case Some(session) if session.root == root => Future.successful(Some(session))
        case Some(stale) =>
          Log
            .future(Source.Bootstrap, s"Discarding state for missing root ${stale.root.value}")
            .flatMap(_ => discard())
            .map(_ => None)
        case None => Future.successful(None)
      }

  private def discard(): Future[Unit] =
    store.clear().recover { case e => Log(Source.Bootstrap, s"Cannot discard session state: $e") }

  private def reconcile(root: BookmarkNode.Id): Future[SessionState] =
    for {
      _ <- Log.future(Source.Bootstrap, s"Reconciling ${root.value} with tab groups")
      state <- StartupSync(root, bookmarks, SyncPlanner(root), tabGroups, tabs).synchronize()
      session = SessionState(root, state)
      _ <- save(session)
    } yield session

  private def save(session: SessionState): Future[Unit] =
    store.save(session).recover { case e =>
      Log(Source.Bootstrap, s"Cannot save session state: $e")
    }

}
