# Firefox Tab Groups & Bookmarks Sync

Two-way synchronization between Firefox tab groups and bookmark folders.

When you create, rename, or delete a tab group, the corresponding bookmark
folder is updated automatically — and vice versa. This lets your tab groups
persist across browser restarts and gives you a browsable, searchable copy of
them in your bookmarks.

## Features

- **Two-way sync** — tab group changes propagate to bookmarks; bookmark changes
  propagate to tab groups.
- **Conflict resolution** — last-write-wins when both sides change
  simultaneously.
- **Startup reconciliation** — on extension load, bookmark folders are the
  source of truth and tab groups are recreated to match.

## Requirements

- Firefox 139+
- sbt 1.10.x
- JDK 17+
- Scala 3.3.x LTS

## Development

### Compile and package

```bash
sbt packageDev
```

Output goes to `dist/` as `main.js`, `main.js.map`, and `manifest.json`.

### Load into Firefox (temporary)

1. Open `about:debugging#/runtime/this-firefox`
2. Click **Load Temporary Add-on…**
3. Select `dist/manifest.json`

The extension runs until you restart Firefox. Reload after `sbt packageDev` to pick up changes.

## API Reference

- [MDN WebExtensions API](https://developer.mozilla.org/en-US/docs/Mozilla/Add-ons/WebExtensions/API)
  - [`bookmarks`](https://developer.mozilla.org/en-US/docs/Mozilla/Add-ons/WebExtensions/API/bookmarks)
  - [`tabGroups`](https://developer.mozilla.org/en-US/docs/Mozilla/Add-ons/WebExtensions/API/tabGroups)
  - [`tabs`](https://developer.mozilla.org/en-US/docs/Mozilla/Add-ons/WebExtensions/API/tabs)

## License

MIT — see [LICENSE](LICENSE).

## Credits

- Icon: [Tabler Icons](https://tabler.io/icons) (folder-share, MIT License)
