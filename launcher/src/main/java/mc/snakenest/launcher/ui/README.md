# ui

The Swing shell shared by every page: logo, top bar, navigation sidebar,
and a `CardLayout` content area - see `devpreview.ShellPreview` to run it
standalone with placeholder pages.

- **`LauncherFrame`** — the composition point. Built once from `Main` (or
  `devpreview.ShellPreview`), pages are added with `addPage(NavTarget, JComponent)`
  and never constructed by this package itself - it has no dependency on
  `ui.modpack`/`ui.news`/`ui.settings`/`ui.account`.
- **`Sidebar`** — Modpacks / Actualites (mutually exclusive nav, like tabs),
  Twitch / Discord (external links, not pages), Settings pinned to the
  bottom. Icons come from `ui.common.Icons`.
- **`TopBar`** — page title + account button. A page can temporarily
  replace the title with a back button (`LauncherFrame#showBackButton`),
  for a modpack's detail view (see the third mockup).
- **`LogoPanel`** — placeholder brand mark (plain "SN" square). The
  mockups use a plain black square for the same reason: they're a rough
  layout sketch, not the final look - swap this out once a real logo asset
  exists.
- **`ThemeController`** — applies FlatLaf (light/dark) and persists the
  choice via `config.ConfigStore`. `applyStartupTheme()` must run before any
  `JFrame` is constructed; `switchTo()` can run any time after and updates
  every open window live via `FlatLaf.updateUI()`.
- **`NavTarget`** — `NEWS`/`MODPACKS`/`SETTINGS`, the shared vocabulary
  between `Sidebar` and `ContentArea`.

## `ui.common`

Small reusable pieces, not specific to any one page:

- **`Icons`** / **`VectorIcon`** — hand-drawn (Java2D) icons rather than a
  bundled raster/SVG set (none exists yet). Follows the current theme's
  foreground color automatically, so light/dark switching is "free". Twitch
  and Discord are deliberately generic glyphs, not the real trademarked
  logos - see `Icons`'s Javadoc.
- **`IconButton`** — flat icon-only `JToggleButton`, used for the sidebar's
  mutually-exclusive nav buttons.
- **`Buttons`** — `flatIcon(...)`, a plain (non-toggle) flat icon button
  factory used for external links and page action buttons.
