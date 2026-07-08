# ui.modpack

The "Modpacks" section: a list of accessible modpacks and one modpack's
detail view (install/update progress, description, "Demarrer").

- **`ModpackSectionPage`** — what actually gets registered under
  `NavTarget.MODPACKS` in `LauncherFrame`. Owns a small internal
  `CardLayout` between the list and the detail view, since going from one
  to the other is a sub-navigation *within* this section, not a top-level
  sidebar page - `LauncherFrame`'s own `showBackButton` is used alongside
  it (see `Main` for the real wiring, `devpreview.FullShellPreview` for a
  fake-data example). `showDetail` takes any `JComponent`, not just a
  `ModpackDetailPage` - `LauncherApp#showModpackDetail` shows a
  `ui.common.LoadingPanel` there immediately on click, swapping in the real
  `ModpackDetailPage` once its manifest fetch completes, so the transition
  itself never waits on the network. A manifest fetch failure now shows an
  error dialog and returns to the list, instead of silently doing nothing.
- **`ModpackListPage`** / **`ModpackListViewModel`** — one `ModpackCardView`
  per accessible modpack. `onSelect` (clicking the row) opens the detail
  page; `onQuickAction` (clicking the icon itself) installs+launches (or
  installs) right away - these used to be the same callback, so the icon
  didn't actually do anything a click anywhere else on the row didn't
  already do. Refreshing this list is done via the topbar's "Actualiser"
  button (`ui.TopBar`/`ui.LauncherFrame#setOnRefresh`), not a button on this
  page - the same button also refreshes the modpack detail page and the
  news list, so it doesn't belong to any one of them (moved out of a
  per-page toolbar here for that reason).

  Each card's action icon is a state machine now (`ModpackCardView.ButtonState`,
  mirroring `ModpackDetailPage`'s below) rather than a fixed play/download
  icon computed once at construction: at rest, download/"Mettre a jour"/play
  depending on `installed`/`updateAvailable` (same three-way logic as the
  detail page), an X ("Annuler") while a quick-installed sync/install is in
  flight, a filled square ("Arreter") while a quick-launched game is
  running. `LauncherApp` pushes these transitions to the right card by slug
  via `ModpackListViewModel#setCardBusy`/`#setCardRunning`/`#setCardInstalled`/
  `#setCardUpdateAvailable` (`ModpackListPage` registers each card under
  its modpack's slug as it builds them) - the same install/repair/launch
  code paths used by the detail page's button now update *both* the detail
  page (if open) and the corresponding list card, so a quick action started
  from the list, or an install started from an open detail page, is
  reflected consistently in both places rather than only wherever it was
  started from.
- **`ModpackDetailPage`** / **`ModpackDetailViewModel`** — logo, file
  count/size, description, and the settings/folder/action buttons. The
  description `JTextArea` is `setFocusable(false)` in addition to
  `setEditable(false)` - the latter alone still let it take focus and show
  a blinking text-input caret on click, which reads as an editable field
  even though typing does nothing. The main
  action button is a small state machine (`ModpackDetailPage.ButtonState`):
  at rest (`IDLE`), it reads "Telecharger" if never installed, "Mettre a
  jour" if installed but `updateAvailable` (the locally-recorded manifest
  version - `modpack.StoredManifest#version` - differs from the server's
  latest), or "Demarrer" otherwise - `onDemarrer` is the exact same
  sync+install(+launch) action in all three cases, only the label/icon
  differ, so updating a modpack is just "install" run again against a
  newer manifest. `LauncherApp#isUpdateAvailable` computes this by
  comparing against `LocalManifestStore`, both for the list
  (`updateAvailableSlugs`) and the detail page; a successful install/repair
  clears it back to false via `setUpdateAvailable`/`setCardUpdateAvailable`
  the same way `setInstalled(true)` already did. "Annuler" (`Icons.cancel`,
  an X) while `setBusy(true)` (a sync/install is running - clicking calls
  `onCancel`), "Arreter" (`Icons.stop`, a filled square) while
  `setRunning(true)` (the game process is running - clicking calls
  `onStop`), or "Tuer" (`Icons.skull`) while `setStopping(true)` (clicking
  calls `onKill`). One button, one relevant action at a time. `setBusy(false)`
  only reverts to idle if the state is still `BUSY`; `setRunning(false)`
  reverts to idle from either `RUNNING` or `STOPPING` - a successful install
  immediately followed by launching the game calls `setRunning(true)`
  *before* the caller's `finally` block gets to call `setBusy(false)`, and
  without that first guard the busy-cleanup would clobber the freshly-set
  running state back to idle. Install/sync progress arrives asynchronously
  (`setStatus`/`setProgress` hop to the EDT themselves, safe to call from
  any thread) since installing a modpack runs off the UI thread.

  **"Tuer"/force-kill** (`LauncherApp#stopGame`/`#killGame`): `onStop`
  (`Process#destroy()`, SIGTERM on Linux/macOS) only *asks* the game to
  exit - a native graphics/mod cleanup hang on shutdown can leave it running
  with no visible feedback, previously recourse-free short of a manual
  `kill -9` from a terminal. Clicking "Arreter" now immediately flips the
  button to "Tuer" (`setStopping(true)`, a no-op unless the button is
  currently `RUNNING` - safe to call unconditionally on whichever page/card
  happens to be on screen) so the user has an explicit, immediate
  `destroyForcibly()` (SIGKILL, unconditional) available rather than waiting
  - `stopGame` also starts its own background force-kill after a grace
  period as a backstop in case they don't.

  **A brand new `ModpackDetailPage`/card always starts `IDLE`** - neither
  has any way to know an install is in progress or a game is already
  running/being stopped for its modpack, since `installed`/`updateAvailable`
  are the only state `ModpackDetailViewModel`/`ModpackListViewModel` carry
  in from the outside. That's fine the *first* time (the live instance
  already gets `setBusy`/`setRunning`/`setStopping` pushed to it directly as
  it happens) but not on a *subsequent* build of the same page/card -
  `LauncherApp#showModpackDetail`/`#loadModpackList` rerun on every
  "Actualiser" and every reopen from the list, each building a brand new
  page/card from scratch. Installing/downloading a modpack used to have no
  tracking for this at all (unlike running, which at least had a slug
  tracked) - refreshing or reopening a modpack's page mid-download silently
  reverted the button to "Demarrer" even though it was actively
  downloading, which read as the whole button-state system being
  unreliable rather than one specific untracked case.

  Fixed by `LauncherApp#active`: one field (an `ActiveModpack` record - slug
  + which of `INSTALLING`/`RUNNING`/`STOPPING` + whatever `Future`/`Process`
  goes with that) replacing four previously-independent fields that could
  each only describe *part* of "what's happening right now" and had to be
  kept in sync by hand at every call site - installing had no field at all,
  which is exactly how this bug happened in the first place. Every method
  that starts/advances/ends an activity replaces this one reference
  wholesale (`installAndLaunch`, `launchGame` on install success,
  `stopGame`, the exit callback, `clearActiveIfInstalling` on
  failure/cancellation) instead of touching several fields separately -
  `LauncherApp#restoreActiveState`/`#restoreActiveCardState` are the one
  place a freshly built page/card reads it back, covering all three
  activities the same way rather than a bespoke check per state that's easy
  to forget writing for the next new one. Also closes a subtler race: the
  activity is recorded *before* the background task is even submitted, not
  after - otherwise a background task fast enough to finish before that
  line ran on the caller's thread could have its own (correct, newer) state
  silently overwritten by the caller's stale write.

  **The same staleness bug existed everywhere else in the install/launch
  chain, not just the exit callback** - the next report was "I had to
  refresh for the button to go from Annuler to Arreter", i.e. a live push
  mid-flow (not just the two endpoints above) silently failed to reach the
  page actually on screen. `doInstallAndLaunch`/`syncModpackFiles`/
  `installGame`/`launchGame`/`warnIfUsernameChanged` used to all take a
  `ModpackDetailPage` parameter, captured *once* at the very start of
  `installAndLaunch`/`quickInstallAndLaunch`/`repairModpack` and threaded
  through every subsequent call - every `setStatus`/`setProgress`/
  `setRunning`/`setBusy` push along the way (a whole install can easily run
  for tens of seconds to minutes) went to that one snapshot, which stopped
  being the visible page the moment the user refreshed or left-and-reopened
  mid-install.

  Fixed by giving `ModpackDetailPage` a stable `slug()` (from
  `ModpackDetailViewModel`) and replacing every one of those parameters
  with `LauncherApp#withDetailPage(slug, action)`: looks up
  `currentDetailPage` fresh *at the moment of each individual push* and
  only applies `action` if it's still showing `slug` - a no-op otherwise
  (page rebuilt, navigated away, or never open to begin with), same "safe
  no-op" philosophy as everywhere else in this class. This is strictly more
  correct than the old "detailPage != null" check too: that only asked "was
  a page open when this operation *started*", not "is a page open *now*, and
  is it still the right one" - a failure reported after the user had
  navigated away used to silently vanish onto an invisible stale page
  instead of falling back to the error dialog (`LauncherApp#isDetailPageShowing`
  now drives that fallback decision correctly for the same reason). The settings/folder icons use
  `ui.common.Buttons#iconButton` (visible background at rest, generous
  margin so they read closer to the main button's height), not `#flatIcon`
  (background only on hover, smaller) - the latter made them look like bare
  glyphs rather than buttons.

  The gear button opens a small menu - Gerer/Reparer/Desinstaller:
  - **Gerer** opens `ModpackSettingsDialog` (memory allocation + custom JVM
    args, see `modpack.ModpackSettings`/`ModpackSettingsStore`), saved via
    `onSaveSettings`. The dialog is always shown with `currentSettings` (a
    field on `ModpackDetailPage`, updated on every save), not
    `viewModel.settings()` directly - `ModpackDetailViewModel` is an
    immutable record frozen at construction time, so reading straight from
    it meant reopening "Gerer" right after saving showed the stale
    pre-save values again (the save itself worked - `ModpackSettingsStore`
    writes correctly, and `LauncherApp#launchGame` reloads fresh settings
    from disk at launch time - only the dialog's *own* re-display was
    wrong).
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
  subtly to read as a card. The action button similarly paints its own
  *circular* rollover/pressed background (an anonymous `JButton` override,
  same technique as `ui.common.IconButton` - duplicated rather than shared,
  since this button has no "selected" state to speak of, being a fire-once
  action rather than a toggle) instead of FlatLaf's default "toolBarButton"
  rounded-square hover, to match the sidebar's round nav buttons. Also
  needed `actionWrapper` to switch from `BorderLayout` to `GridBagLayout`:
  `BorderLayout.CENTER` stretches its child to fill all remaining space
  regardless of that child's own max size, which would silently defeat the
  button's fixed circle diameter (`ACTION_BUTTON_SIZE`) the same way an
  unset maximum defeats `IconButton`'s `diameter` in a `BoxLayout` - two
  different layout managers, same underlying lesson: a fixed-size circular
  button needs its container to actually respect that size, not just the
  button's own preferred-size declaration.

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
Installing/uninstalling from the detail page *does* update the corresponding
list card live now (`setCardInstalled`, see above) as long as that list is
still the one currently shown - a list replaced by a refresh mid-install
just silently stops receiving updates for the old instance, same as a
stale `ModpackDetailPage` reference.
