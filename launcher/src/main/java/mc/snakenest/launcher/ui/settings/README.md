# ui.settings

- **`SettingsPage`** — theme toggle (light/dark radio buttons, calls
  `ui.ThemeController#switchTo` via a `Consumer<Theme>` callback so this
  page doesn't depend on `ThemeController` directly), a data-folder
  shortcut, and logout. Kept intentionally small; grows here as real
  settings (RAM, Java path, etc.) get added later.
