# ui.about

- **`LicensesDialog`** — the "Voir les licences tierces" button in
  `ui.settings.SettingsPage`'s "A propos" section opens this. A
  dependency list next to the full text of whichever one is selected,
  loaded at runtime from the jar's own `licenses/` resources
  (`src/main/resources/licenses/`) rather than duplicating license texts
  in source. The dependency list itself (`LicensedDependency`, name +
  version + license + resource file) is a hand-maintained copy of
  `THIRD-PARTY-NOTICES.md`'s dependency table one level up - keep both in
  sync when a dependency changes.
- **`LicensedDependency`** — one row of that list; package-private, an
  implementation detail of `LicensesDialog`.

**Why this exists**: `launcher` is GPL-3.0 (it embeds FlowUpdater/
OpenLauncherLib/FlowMultitools), and GPL requires each recipient of the
jar to be able to get the Corresponding Source - in practice, since this
jar is downloaded by the general public, that means the source has to be
publicly available (GitHub) with an easy-to-find pointer to it from the
app itself, which is what the "A propos" section's link is for. The
license list here is a courtesy on top of that (so a player doesn't have
to go digging through the repo to see what's bundled), not itself part of
the GPL disclosure requirement.

**Version display**: read via `Package#getImplementationVersion()`,
populated at build time by `launcher/pom.xml`'s shade-plugin
`manifestEntries` (`Implementation-Version` set to `${project.version}`)
- returns `null` (falls back to "dev") when run from an IDE/unshaded
classpath, since there's no jar manifest to read it from there.
