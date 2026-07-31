package tabgroupsbookmarkssync.service

import tabgroupsbookmarkssync.model.{BookmarkNode, Tab, TabGroup}

enum SyncError {
  case FolderAlreadyPresent(folder: BookmarkNode.Folder, tabGroup: TabGroup)
  case FolderNotFoundForTabGroup(tabGroup: TabGroup)
  case FolderNotFoundForTabGroupId(tabGroupId: TabGroup.Id)
  case MissingUrlForTab(tab: Tab)
  case BookmarkNotFoundForTab(tab: Tab)
}
