# SnakeN3stLauncher

Desktop launcher (Java/Swing) for a semi-public Minecraft server: passwordless
device-flow login, a versioned modpack repository synced by content hash,
and Minecraft install/launch - talking to the Azuriom plugins in the sibling
`SnakeN3stLogin` repository (`LAUNCHER_INTEGRATION.md` there is the
authoritative API contract).

## Modules

A single Maven reactor, three modules:

- **`common`** — tiny, dependency-free utilities (`AppDirs`, hex/SHA-256,
  atomic file writes, logging) shared by the other two, so they can't drift
  apart on something as basic as "where does data live on disk".
- **`bootstrap`** — the small stub actually given to players. Checks the
  site for the latest launcher release, downloads + verifies it, runs it,
  exits. No GPL code.
- **`launcher`** — the complete application. Embeds FlowUpdater and
  OpenLauncherLib (both GPL-3.0), so **this module is distributed under
  GPL-3.0** - see `launcher/LICENSE` and `THIRD-PARTY-NOTICES.md`.

See each module's own `README.md`/package `README.md` files for the
per-package breakdown, and the plan this was built from
(`.claude/plans` if still present, or ask for a recap) for the full
architecture rationale.

## Building

```bash
mvn clean package
```

Produces `bootstrap/target/snaken3st-launcher-bootstrap-*.jar` and
`launcher/target/snaken3st-launcher-*.jar` (both runnable, `java -jar ...`).

## Running during development

Both jars read two JVM system properties:

- `sn3.baseUrl` — the Azuriom site's URL. Defaults to the production URL;
  during development, point it at a local instance, e.g.
  `-Dsn3.baseUrl=http://127.0.0.1`.
- `sn3.clientId` — a `LauncherClient` id registered via `/admin/launcher-auth`
  on that site. Required, no default.

```bash
java -Dsn3.baseUrl=http://127.0.0.1 -Dsn3.clientId=<id> -jar launcher/target/snaken3st-launcher-*.jar
```

The bootstrap forwards both properties to whatever launcher jar it spawns,
so testing it end-to-end uses the same two flags.

### Manual QA without any network/auth

`launcher`'s `mc.snakenest.launcher.devpreview` package has one runnable
`main()` per screen (and one for the whole shell,
`FullShellPreview`), wired to fake in-memory data - run any of them
directly from IntelliJ to eyeball a page without needing a real login or
server.

## Tests

```bash
mvn test
```

Pure-logic classes (crypto, the modpack hash-diff engine, path safety, etc.)
are covered by deterministic JUnit tests. A few tests are named
`*LocalAzuriomSmokeTest` and hit a real local Azuriom instance at
`http://127.0.0.1` when one is reachable (skip themselves otherwise via
`Assumptions`) - useful during development, not required for the suite to
pass elsewhere.
