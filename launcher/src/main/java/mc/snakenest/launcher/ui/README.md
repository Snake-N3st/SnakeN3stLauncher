# ui

The Swing shell shared by every page: logo, top bar, navigation sidebar,
and a `CardLayout` content area - see `devpreview.FullShellPreview` to run
it standalone with fake data.

- **`LoginFrame`** — the very first screen, shown before any session exists
  (no sidebar/pages to put anything in yet). By the time this frame is
  constructed, `LauncherApp` has already fetched the client's name/logo
  (blocking, off the EDT - see `LauncherApp#start`'s Javadoc) and calls
  `setClientInfo` right before `setVisible(true)`, so the window never
  flashes a placeholder then the real branding. Also gets `setIconImage`d
  with the same logo, and shows a short static welcome message under the
  title. Shows an indeterminate progress bar while busy and the failure
  reason in a distinct color on error.
- **`LauncherFrame`** — the composition point, shown after login. Built
  once from `Main`/`LauncherApp` (or `devpreview.FullShellPreview`), pages
  are added with `addPage(NavTarget, JComponent)` and never constructed by
  this package itself - it has no dependency on
  `ui.modpack`/`ui.news`/`ui.settings`/`ui.account`. `setLogo(BufferedImage)`
  swaps in the real client logo (same pre-fetched-before-show approach as
  `LoginFrame`, including `setIconImage`); `setAccountAvatar(BufferedImage)`
  does the same for the account button's icon (see `TopBar`).
- **`Sidebar`** — Modpacks / Actualites (mutually exclusive nav, like tabs),
  Settings pinned to the bottom. No external links (Twitch/Discord) anymore
  - removed as clutter. Icons come from `ui.common.Icons`; the two main nav
  icons (34px) are deliberately bigger than the Settings gear (26px, a
  secondary/utility icon) - both went through two size passes after reading
  as too small.
- **`TopBar`** — page title + refresh button + account button (in that
  order, right-aligned via a `rightPanel`). A page can temporarily
  replace the title with a back button (`LauncherFrame#showBackButton`),
  for a modpack's detail view (see the third mockup). `leftPanel` uses a
  `BorderLayout` (not `FlowLayout`) specifically so the title/back-button
  vertically center within the bar's full height - a `FlowLayout` row only
  ever takes its components' preferred height, which pinned the title near
  the top whenever the bar was taller than the label (e.g. to match the
  logo next to it). `rightPanel` uses `GridBagLayout` for exactly the same
  reason, not `FlowLayout` - confirmed by dumping real bounds that
  `FlowLayout` centers a row *within its own height* but then pins that row
  to the *top* of any extra height the container is actually given (the
  account button sat flush at `rightPanel`'s `y=0`, all the slack pushed
  below it, not split top/bottom); `GridBagLayout`'s default constraints
  (`anchor=CENTER`) center properly regardless of any size mismatch.
  `setAccountAvatar` swaps the generic user-circle icon
  for the real account avatar (`ui.common.RoundedImageIcon`), fetched once
  by `LauncherApp` alongside the rest of `auth.PlayerInfo`. The account
  button has an explicit fixed size (`ACCOUNT_BUTTON_SIZE`, bigger than
  just the icon) rather than whatever size the look-and-feel derives from
  the icon alone - same reasoning as `ui.common.IconButton`'s fix below:
  leaving it implicit risked an inconsistent/off-center result, and read
  as noticeably small next to the other topbar/sidebar icons.
  `setOnRefresh(Runnable)` shows the refresh button bound to that action, or
  hides it entirely for `null` - lives on `TopBar`/`LauncherFrame` rather
  than on any one page since the same button doubles as the modpack list's,
  the modpack detail page's, and the news list's "Actualiser", and only one
  of those is ever the current page.
- **`LogoPanel`** — the brand mark shown in both `LoginFrame` and
  `LauncherFrame`: the launcher client's real logo once fetched
  (`auth.LauncherAuthApiClient#fetchClientInfo` + `ui.common.RemoteImages`),
  or a placeholder scroll/parchment glyph before it loads / if none is set
  (previously a plain "SN" square with lettering - changed since a brand
  mark reading as a random pair of initials wasn't communicating anything).
  Package-private - `LauncherFrame`/`LoginFrame` each own one and expose a
  `setLogo`/`setClientInfo` method rather than exposing the panel itself.
  The real logo is drawn preserving its aspect ratio, centered within the
  square slot, not force-stretched to fill it - a client's logo is often a
  non-square wordmark, and stretching one doesn't just distort it, it reads
  as "not centered" too (the visual subject sits off to one side once
  squished even though the square region itself is centered). Confirmed
  with a synthetic 300x100 test image before/after.

  Also sets `setMinimumSize`/`setMaximumSize` to the same `size` as
  `setPreferredSize` - the actual root cause of a lingering "logo still not
  centered" report turned out to have nothing to do with the painting
  logic above (which was already correct): `LoginFrame`'s `center` uses
  `BoxLayout.Y_AXIS`, and without an explicit maximum this panel competed
  with the trailing `Box.createVerticalGlue()` for leftover space instead
  of staying a fixed size. Confirmed by dumping real bounds: an 88px
  `LogoPanel` there rendered at 452x189 (stretched to the column's full
  width, and taller too) instead of 88x88 - same root cause as `ui.common.IconButton`'s
  fix, just a much larger, more visually-obvious symptom since it's a
  `BoxLayout.Y_AXIS` context rather than `BorderLayout` (which is why
  `LauncherFrame`'s header usage of this same class never showed the bug -
  `BorderLayout.WEST` always uses exactly the child's preferred width,
  unaffected by an unset maximum).

  In `LauncherFrame` specifically, "centered" turned out to mean something
  more precise than "centered within its own 64px square" - the user's own
  definition: centered within a zone exactly as wide as `Sidebar.WIDTH`
  (82px), the same width as the nav column directly below it, so the two
  visually read as one continuous "left rail" rather than the logo just
  sitting flush at the window's left edge. `LauncherFrame` wraps `logoPanel`
  in a `logoWrapper` (`GridBagLayout`, `setPreferredSize(Sidebar.WIDTH,
  ...)`) for exactly this - `Sidebar.WIDTH` is package-visible (not
  `private`) specifically so `LauncherFrame` can reference it here instead
  of duplicating the number. Confirmed by dumping real bounds: wrapper
  spans `x=0..82` (flush with `Sidebar`'s own `x=0..82` below it), logo
  centered at `x=9` (`(82-64)/2`).
- **`ThemeController`** — applies FlatLaf (light/dark) and persists the
  choice via `config.ConfigStore`. `applyStartupTheme()` must run before any
  `JFrame` is constructed; `switchTo()` can run any time after and updates
  every open window live via `FlatLaf.updateUI()`.
- **`NavTarget`** — `NEWS`/`MODPACKS`/`SETTINGS`, the shared vocabulary
  between `Sidebar` and `ContentArea`.
- **`LauncherFrame#setOnNavigate`** — fired every time a top-level
  (sidebar-driven) navigation happens, so `LauncherApp` can rebind
  `setOnRefresh` to whatever the newly-shown page can refresh. Only fires
  for real navigation (`navigateTo`/the sidebar) - sub-navigation within a
  section (e.g. opening a modpack's detail page) never calls `navigateTo`,
  so `LauncherApp` binds `setOnRefresh` directly at those call sites instead.
- **`Resettable`** — implemented by a page with its own internal
  sub-navigation (`ui.modpack.ModpackSectionPage`, `ui.news.NewsSectionPage`)
  so `ContentArea#show` can put it back to its default (list) view every
  time the user navigates to it. Without this, opening a modpack's detail
  view, switching to "Actualites", then back to "Modpacks" showed the title
  "Modpacks" but kept displaying the stale detail page, with no back button
  to get out of it - `LauncherFrame#navigate` resets the title/back-button,
  but had no way to know a section needed its own content reset too.

## `ui.common`

Small reusable pieces, not specific to any one page:

- **`Icons`** / **`VectorIcon`** — hand-drawn (Java2D) icons rather than a
  bundled raster/SVG set (none exists yet). Follows the current theme's
  foreground color automatically, so light/dark switching is "free". No
  Twitch/Discord glyphs anymore - the sidebar shortcuts they were for got
  removed. `cancel()`/`stop()`/`skull()` (X / filled square / a small
  outlined skull with filled eye sockets, nose, and teeth - drawn like
  `gear()`/`folder()`, not filled, so those read as solid dots against it
  without needing to know the surrounding background color) back the detail
  page's and the modpack list's Annuler/Arrêter/Tuer button states - see
  `ui.modpack` README for the "Tuer" force-kill state itself. Legibility at
  the actual usage sizes (18-28px) confirmed by rendering and screenshotting
  it directly rather than eyeballing the drawing code alone.

  `refresh()` is the one exception to "hand-drawn Java2D shape" in this
  class: it draws the "⭮" glyph (U+2B6E) via `Graphics2D#drawString`
  instead. Three vector attempts (an open chevron on an arc, a filled
  triangular arrowhead on an arc, two point-symmetric arrow+arc pairs) all
  read as an ambiguous blob/hook at toolbar sizes (~20-24px) - a real
  font's small-size hinting does a better job of exactly this shape than a
  few dozen manually-plotted polyline points can. Uses the platform's
  logical `SANS_SERIF` font (so its normal fallback finds whatever
  installed font actually covers this codepoint) - confirmed rendering
  correctly on this dev machine (Linux, via Symbola/Noto Sans Symbols2),
  not verified on Windows/macOS; if it ever shows as a tofu box there,
  that's the first thing to check.
- **`IconButton`** — icon-only `JToggleButton` used for the sidebar's
  mutually-exclusive nav buttons. Paints its own circular background
  (selected, or on hover/press) instead of using FlatLaf's default
  "toolBarButton" shape, which is a rounded square - a nav rail reads
  better with round buttons. Sets `setMinimumSize`/`setMaximumSize` to the
  same `diameter` as `setPreferredSize`, not just the latter - without an
  explicit maximum, `Sidebar`'s `BoxLayout.Y_AXIS` fell back to
  `getMaximumSize()`'s look-and-feel-computed default (roughly icon size +
  a few px), which silently shrank every button's actual on-screen width
  below the requested `diameter` regardless of its value (confirmed by
  dumping real component bounds: a 64px `diameter` rendered as ~40px for
  the 34px nav icons and ~32px for the smaller 26px Settings gear) - so
  Settings' rollover circle read as smaller than the nav buttons' despite
  both supposedly sharing the same `SELECTION_DIAMETER` (`Sidebar`). Fixed;
  `SELECTION_DIAMETER` stayed at 64 (a first pass bumped it to 68 once the
  fix let it actually take effect, but that read as too big and was
  reverted - 64 was already a big jump up from the ~40/32px the bug had
  silently been rendering).
- **`Buttons`** — `flatIcon(...)`, a plain (non-toggle) flat icon button
  whose background only shows on hover (external links); `iconButton(...)`,
  which looks like an actual button at rest too (the modpack detail page's
  settings/folder actions - `flatIcon` there made them read as bare glyphs
  instead of buttons).
- **`AvatarPanel`** — rounded-square letter placeholder for a modpack/account
  with no image (no image loaded/set) - unrelated to `LogoPanel`, which is
  specifically the app/client brand mark, not a per-item avatar. Centers
  itself within whatever bounds its container gives it (padding included)
  rather than drawing corner-to-corner, since a `BorderLayout.WEST`/`EAST`
  slot stretches the component to the full container height regardless of
  its preferred/max size - without that centering the square ended up
  pinned to the top-left with no margin whenever the container was taller
  than the square itself (see `ui.modpack.ModpackCardView`).
- **`RemoteImages`** — best-effort image download+decode from a URL (a
  client's logo, a modpack's icon, an account avatar). Does real network
  I/O - callers must run it off the EDT - and never throws: any failure
  just returns `null`, callers fall back to a placeholder rather than
  breaking a screen over a missing/broken image.
- **`RoundedImageIcon`** — a `BufferedImage` clipped to a rounded *square*,
  sized to drop into a button/label's icon slot. Used for the account
  avatar in `TopBar`/`ui.account.AccountPopover` - unlike `LogoPanel`, the
  avatar isn't its own component, just a button's icon, so a plain `Icon`
  is all that's needed. Square rather than circular on purpose: Azuriom
  sites conventionally show square avatars (a player's Minecraft head
  render is square), so a circular crop would look inconsistent with the
  site the player came from. Doesn't assume the source is *exactly* square
  though - scales preserving aspect ratio and centers within the square
  (same fix and reasoning as `LogoPanel`'s, above) rather than
  force-stretching, in case a real avatar source isn't pixel-perfect
  square in practice.
- **`Colors`** — `danger()`, the one red used for error text everywhere
  (login failure, install/repair/uninstall failure) - centralized so
  "what red means here" isn't reinvented (and risks drifting) per screen.
- **`LoadingPanel`** — a centered label + indeterminate progress bar, shown
  as an immediate placeholder while something loads over the network (e.g.
  `LauncherApp#showModpackDetail` shows one the instant a modpack card is
  clicked, before the manifest fetch that builds the real detail page even
  starts) - a page "transition" that visibly does nothing until a network
  call completes reads as the app hanging, especially on a slow connection.
- **`ErrorPanel`** — the failure counterpart to `LoadingPanel`: a centered
  error message + "Réessayer"/"Retour" buttons, replacing a page's content
  in place rather than the old behavior (`LauncherApp#showModpackDetail`'s
  manifest-fetch failure path used to bounce back to the list plus a modal
  `JOptionPane`) - spamming "Actualiser" into a 429 used to leave the page
  looking stuck/broken with no obvious way to retry short of re-opening
  the modpack from the list. The message is rendered as escaped HTML
  purely to get word-wrapping at a fixed width (`WRAP_WIDTH_PX`) - a plain
  `JLabel` never wraps and just runs off the edge for a long exception
  message; escaped since the message ultimately comes from
  `Exception#getMessage()`, not trusted input.
