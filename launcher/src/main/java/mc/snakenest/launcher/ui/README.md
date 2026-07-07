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
- **`TopBar`** — page title + account button. A page can temporarily
  replace the title with a back button (`LauncherFrame#showBackButton`),
  for a modpack's detail view (see the third mockup). `leftPanel` uses a
  `BorderLayout` (not `FlowLayout`) specifically so the title/back-button
  vertically center within the bar's full height - a `FlowLayout` row only
  ever takes its components' preferred height, which pinned the title near
  the top whenever the bar was taller than the label (e.g. to match the
  logo next to it). `setAccountAvatar` swaps the generic user-circle icon
  for the real account avatar (`ui.common.RoundedImageIcon`), fetched once
  by `LauncherApp` alongside the rest of `auth.PlayerInfo`.
- **`LogoPanel`** — the brand mark shown in both `LoginFrame` and
  `LauncherFrame`: the launcher client's real logo once fetched
  (`auth.LauncherAuthApiClient#fetchClientInfo` + `ui.common.RemoteImages`),
  or a placeholder scroll/parchment glyph before it loads / if none is set
  (previously a plain "SN" square with lettering - changed since a brand
  mark reading as a random pair of initials wasn't communicating anything).
  Package-private - `LauncherFrame`/`LoginFrame` each own one and expose a
  `setLogo`/`setClientInfo` method rather than exposing the panel itself.
- **`ThemeController`** — applies FlatLaf (light/dark) and persists the
  choice via `config.ConfigStore`. `applyStartupTheme()` must run before any
  `JFrame` is constructed; `switchTo()` can run any time after and updates
  every open window live via `FlatLaf.updateUI()`.
- **`NavTarget`** — `NEWS`/`MODPACKS`/`SETTINGS`, the shared vocabulary
  between `Sidebar` and `ContentArea`.
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
  removed.
- **`IconButton`** — icon-only `JToggleButton` used for the sidebar's
  mutually-exclusive nav buttons. Paints its own circular background
  (selected, or on hover/press) instead of using FlatLaf's default
  "toolBarButton" shape, which is a rounded square - a nav rail reads
  better with round buttons.
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
  site the player came from.
