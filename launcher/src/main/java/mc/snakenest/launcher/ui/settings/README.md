# ui.settings

- **`SettingsPage`** — theme toggle (light/dark radio buttons, calls
  `ui.ThemeController#switchTo` via a `Consumer<Theme>` callback so this
  page doesn't depend on `ThemeController` directly), a Discord Rich
  Presence checkbox (`config.LauncherConfig#discordEnabled`, calls
  `LauncherApp#setDiscordEnabled` via a `Consumer<Boolean>` - same
  "doesn't depend on the real thing directly" reasoning as the theme
  toggle, see `discord/README.md`), a data-folder shortcut, logout, and
  an "A propos" section (app version, a clickable link to the public
  GitHub repo, and a "Voir les licences tierces" button opening
  `ui.about.LicensesDialog`). Kept intentionally small; grows here as
  real settings (RAM, Java path, etc.) get added later.

  The "A propos" section takes no constructor parameters (unlike every
  other section here) - its content (version, repo URL, dependency list)
  is either read from the jar's own manifest or hardcoded, nothing
  `LauncherApp`/`devpreview.FullShellPreview` needs to inject. Version
  comes from `Package#getImplementationVersion()`, populated by
  `launcher/pom.xml`'s shade-plugin `manifestEntries` - falls back to
  "dev" when run unshaded from an IDE, where there's no jar manifest to
  read it from.
