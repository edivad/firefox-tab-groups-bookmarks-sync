package tabgroupsbookmarkssync.model

/**
 * Synchronization state bound to the sync root folder, as persisted for the browser session.
 *
 * @param root
 *   id of the sync root folder the state belongs to
 * @param state
 *   associations between tab groups, folders, tabs and bookmarks
 */
final case class SessionState(root: BookmarkNode.Id, state: State)
