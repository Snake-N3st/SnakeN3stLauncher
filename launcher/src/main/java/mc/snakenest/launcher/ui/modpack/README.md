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
  view-model. `onSelect` (clicking the row) opens the detail page;
  `onQuickAction` (clicking the icon itself) installs+launches (or installs)
  right away - these used to be the same callback, so the icon didn't
  actually do anything a click anywhere else on the row didn't already do.
- **`ModpackDetailPage`** / **`ModpackDetailViewModel`** — logo, file
  count/size, description, and the settings/folder/action buttons. The main
  action button reads "Telecharger" when `viewModel.installed()` is false
  and "Demarrer" once it's true - same callback either way (sync+install,
  then launch); `setInstalled(true)` flips the label once a fresh install
  finishes, without needing to leave and reopen the page. Install/sync
  progress arrives asynchronously (`setStatus`/`setProgress` hop to the EDT
  themselves, safe to call from any thread) since installing a modpack runs
  off the UI thread. The settings/folder icons use
  `ui.common.Buttons#iconButton` (visible background at rest, generous
  margin so they read closer to "Demarrer"'s height), not `#flatIcon`
  (background only on hover, smaller) - the latter made them look like bare
  glyphs rather than buttons.

  The gear button opens a small menu - Gerer/Reparer/Desinstaller:
  - **Gerer** opens `ModpackSettingsDialog` (memory allocation + custom JVM
    args, see `modpack.ModpackSettings`/`ModpackSettingsStore`), saved via
    `onSaveSettings`.
  - **Reparer** (`onRepair`) clears the locally-recorded manifest
    (`modpack.LocalManifestStore#clear`) and re-syncs+installs from
    scratch, without launching - for a corrupted/partial install.
  - **Desinstaller** (`onUninstall`) deletes the instance directory
    entirely, after a confirmation dialog (destructive, so confirmed here
    in the UI layer before `LauncherApp` ever sees the call).
- **`ModpackSettingsDialog`** — the "Gerer" form (memory spinner + JVM args
  text field), a plain `JOptionPane` confirm dialog rather than a
  hand-rolled `JDialog` - two fields and OK/Cancel don't need more than
  that.
- **`ModpackCardView`** — one row of the list. Not public: an
  implementation detail of `ModpackListPage`. Paints its own rounded-rect
  background/border (`RoundedBorder`) rather than relying on
  `LineBorder(..., true)`'s built-in "rounded" flag, which renders too
  subtly to read as a card.

**Modpack logos**: `ModpackSummary`/the manifest always carried an `image`
URL, but neither `ModpackCardView` nor `ModpackDetailPage` ever loaded it -
both just showed `ui.common.AvatarPanel`'s letter placeholder unconditionally.
Fixed by having `LauncherApp` pre-fetch each modpack's logo (in parallel,
`fetchModpackLogos`) alongside the list, and the single modpack's logo
alongside its manifest for the detail page - same "fetch before show"
approach as the client/account logos, threaded through
`ModpackListViewModel#logoFor`/`ModpackDetailViewModel#logo` into
`AvatarPanel#setImage`.

**Known simplification**: the detail view's size/file-count come straight
from the manifest (`ModpackDetailViewModel`), not from walking the actual
on-disk instance directory - the mockup shows both "in the manifest" and
"actually on disk" figures side by side, which would need real disk usage
computation. Left out for now as a nice-to-have, not core to the feature.
The list's installed/not-installed badges are similarly a snapshot from
when the list was loaded - installing/uninstalling from the detail page
updates that page's own button, but not the list's badge until it's
reloaded (e.g. reopening the Modpacks section).
