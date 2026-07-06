# ui.modpack

The "Modpacks" section: a list of accessible modpacks and one modpack's
detail view (install/update progress, description, "Demarrer").

- **`ModpackSectionPage`** — what actually gets registered under
  `NavTarget.MODPACKS` in `LauncherFrame`. Owns a small internal
  `CardLayout` between the list and the detail view, since going from one
  to the other is a sub-navigation *within* this section, not a top-level
  sidebar page - `LauncherFrame`'s own `showBackButton` is used alongside
  it (see `Main` for the real wiring, `devpreview.FullShellPreview` for a
  fake-data example).
- **`ModpackListPage`** / **`ModpackListViewModel`** — one `ModpackCardView`
  per accessible modpack; the action icon toggles between play (already
  installed) and download (not yet), driven by `installedSlugs` on the
  view-model.
- **`ModpackDetailPage`** / **`ModpackDetailViewModel`** — logo, file
  count/size, description, and the settings/folder/"Demarrer" buttons.
  Install/sync progress arrives asynchronously (`setStatus`/`setProgress`
  hop to the EDT themselves, safe to call from any thread) since installing
  a modpack runs off the UI thread.
- **`ModpackCardView`** — one row of the list. Not public: an
  implementation detail of `ModpackListPage`.

**Known simplification**: the detail view's size/file-count come straight
from the manifest (`ModpackDetailViewModel`), not from walking the actual
on-disk instance directory - the mockup shows both "in the manifest" and
"actually on disk" figures side by side, which would need real disk usage
computation. Left out for now as a nice-to-have, not core to the feature.
