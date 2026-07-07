# ui.settings

- **`SettingsPage`** — theme toggle (light/dark radio buttons, calls
  `ui.ThemeController#switchTo` via a `Consumer<Theme>` callback so this
  page doesn't depend on `ThemeController` directly), a Discord Rich
  Presence checkbox (`config.LauncherConfig#discordEnabled`, calls
  `LauncherApp#setDiscordEnabled` via a `Consumer<Boolean>` - same
  "doesn't depend on the real thing directly" reasoning as the theme
  toggle, see `discord/README.md`), a data-folder shortcut, and logout.
  Kept intentionally small; grows here as real settings (RAM, Java path,
  etc.) get added later.
