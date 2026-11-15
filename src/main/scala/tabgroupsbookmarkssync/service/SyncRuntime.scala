package tabgroupsbookmarkssync.service

import tabgroupsbookmarkssync.config.Config
import tabgroupsbookmarkssync.model.SessionState
import tabgroupsbookmarkssync.spi.{Bookmarks, SessionStore, TabGroups, Tabs}

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success}

/**
 * Connects the extension to the browser lifecycle.
 *
 * [[start]] registers every runtime listener synchronously, before any asynchronous work begins:
 * Firefox only wakes a suspended background page for listeners registered while the page starts, so
 * a listener registered from a future continuation is not a restart trigger.
 *
 * Events received while the extension is still initializing are buffered and replayed in arrival
 * order once the state is available.
 */
final class SyncRuntime(
  config: Config,
  bookmarks: Bookmarks,
  tabGroups: TabGroups,
  tabs: Tabs,
  store: SessionStore,
)(using ExecutionContext) {

  private val pending = mutable.Queue.empty[EventSync => Unit]
  private val ready = Promise[Unit]()
  private var sync: Option[EventSync] = None
  private var failed: Boolean = false

  def start(): Unit = {
    tabGroups.onTabGroupCreate(tabGroup => dispatch(_.tabGroupCreated(tabGroup)))
    tabGroups.onTabGroupUpdate(tabGroup => dispatch(_.tabGroupUpdated(tabGroup)))
    tabGroups.onTabGroupRemove(tabGroup => dispatch(_.tabGroupRemoved(tabGroup)))
    tabs.onTabUpdate(tab => dispatch(_.tabUpdated(tab)))

    initialize().onComplete {
      case Success(initialized) =>
        sync = Some(initialized)
        pending.dequeueAll(_ => true).foreach(action => action(initialized))
        ready.success(())
      case Failure(exception) =>
        failed = true
        pending.clear()
        ready.failure(exception)
    }
  }

  /** Completes when initialization and every event received so far are processed. */
  def whenIdle(): Future[Unit] =
    ready.future.flatMap { _ =>
      sync match {
        case Some(initialized) => initialized.whenIdle().map(_ => ())
        case None => Future.unit
      }
    }

  private def dispatch(action: EventSync => Unit): Unit =
    sync match {
      case Some(initialized) => action(initialized)
      case None if !failed => pending.enqueue(action)
      case None => ()
    }

  private def initialize(): Future[EventSync] =
    Future(Bootstrap(config, bookmarks, tabGroups, tabs, store).resolve()).flatten.map(eventSync)

  private def eventSync(session: SessionState): EventSync =
    EventSync(
      root = session.root,
      initial = session.state,
      bookmarks = bookmarks,
      planner = SyncPlanner(session.root),
      tabGroups = tabGroups,
      tabs = tabs,
      store = store,
    )

}
