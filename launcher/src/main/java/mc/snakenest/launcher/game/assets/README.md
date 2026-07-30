# game.assets

Implements the **"Stockage des ressources vanilla"** setting
(`config.AssetStorageMode`): sharing vanilla Minecraft assets/libraries
between modpacks instead of giving every instance its own full copy
(~600MB of assets + 150-500MB of libraries each, even when several
modpacks run the exact same Minecraft version).

- **`SharedAssetsLinker`** — the whole feature, in two calls wrapped
  around a completely normal `GameInstallService#install`
  (`LauncherApp#installGame`): `preSeed` before, `absorb` after.

**Why this needs no fork of FlowUpdater/OpenLauncherLib.** The obvious
approach - telling FlowUpdater to download into a shared directory - is
impossible without patching it: `VanillaDownloader` derives both
`assets` and the libraries path from the single instance dir passed to
`update()`, in `private` methods with no injection point (checked against
the real decompiled 1.9.4 source, not guessed). Same on the launch side:
`NoFramework` builds the classpath from a `Path` fixed in its
constructor, bypassing its own argument-templating system.

But FlowUpdater already skips any file that's **already present at the
target path** with the right size/SHA-1. So instead of redirecting where
it writes, `preSeed` hardlinks the shared pool into the instance
*first* - FlowUpdater then sees those files as already correct and never
re-downloads them - and `absorb` moves whatever it *did* download fresh
into the pool afterwards, hardlinking it back so the next modpack
benefits too. Both libraries stay untouched, and at launch time
OpenLauncherLib just sees a perfectly ordinary instance directory
(hardlinks are indistinguishable from regular files to any program).

**Two pools**, both mirroring FlowUpdater's own relative layout so one
tree walk handles each (`util.AppDirs#sharedAssetsPool`/
`#sharedLibrariesPool`):
- assets — mirrors the instance's `assets/`. Mojang already names these
  files by their own hash (`objects/xx/xxxx...`), so this is naturally
  collision-free across every Minecraft version.
- libraries — mirrors `libraries/` plus the client jar under `versions/`,
  whose paths already encode group/artifact/version, and **nothing else**.
  `natives/` is never shared (extracted per-instance, platform-specific,
  small).

  That "nothing else" is an **allow-list** (`SHARED_LIBRARY_SUBDIRS`), and
  it matters: it began as a deny-list (`assets`, `natives`) applied while
  walking the whole instance, which silently pooled everything *else* too -
  `mods/`, `config/` and, worst, `saves/`. Two packs whose world files
  happened to match byte-for-byte (two freshly created worlds, say) became
  literally the same file, so an in-place write from one pack could corrupt
  the other's save. Proven on disk, then fixed and pinned by a regression
  test (`neverSharesModsConfigsOrWorldsBetweenModpacks`). An allow-list
  makes that whole class of mistake impossible by construction instead of
  depending on someone remembering to extend an exclusion list.

**`migrate`** is what the "Migrer les modpacks déjà installés" button
calls, and it's what makes the setting mean anything for packs that are
already on disk (`preSeed`/`absorb` only affect *future* installs).
`absorb` alone can't do it: it skips a file already in the pool, which
during an install just means "already a hardlink", but for a pack installed
before sharing was on is exactly the case that matters - pool has the file,
instance holds a second independent copy. So `migrate` also
**deduplicates** (drop the copy, re-link to the pool), which is where the
disk space actually comes back, and reports what it did so the UI can name
a real figure. It also repairs instances damaged by the deny-list bug
above, since fixing the list can't unpick links that already exist.
Migrating *to* `PER_MODPACK` runs the reverse (copy-then-rename, never an
in-place write, which would change every linked instance at once).

For `AssetStorageMode.OFFICIAL_MINECRAFT` the "pool" is the real
`.minecraft` folder instead, resolved by the same OS rules FlowUpdater's
own `IOUtils.getMinecraftFolder()` uses (kept as an independent copy
rather than imported - only `game.flowupdater` may depend on FlowUpdater
types, see that package's README). That folder already holds
`libraries/`/`versions/` at exactly the relative paths this class
mirrors, which is why the same code works for both pool kinds.

**Hardlinks can genuinely fail** (pool and instance on different
filesystems/drives, or a filesystem without hardlink support). The first
failure logs a warning and abandons the rest of *that* call's pooling
rather than partially linking - the install then just downloads whatever
wasn't pre-seeded straight into the instance, i.e. it degrades to exactly
`PER_MODPACK` behavior for that one install, with the game's own files
left intact either way. **Verified for real**, not just reasoned about: a
pool on `/` (ext4) with an instance on `/dev/shm` (tmpfs) produces a real
`EXDEV` ("Invalid cross-device link"), and the game's files survive it
untouched.

Every entry point **reports** that fallback rather than only logging it
(`preSeed`/`absorb` return a boolean, `migrate` sets
`MigrationResult#hardlinksFailed`), because the failure is otherwise
invisible: everything installs and runs correctly, and the only symptom is
disk usage quietly behaving like `PER_MODPACK` while a sharing option is
selected - which looks exactly like the setting not working. `LauncherApp`
turns that into a warning dialog: once per session for installs (it's a
property of the filesystem layout, so it would otherwise fire on every
single modpack and just train the player to dismiss it), and inline in the
result for a migration.

`PER_MODPACK` makes both methods no-ops, leaving the original
fully-independent-per-instance behavior byte-for-byte unchanged.

**Changing the setting only affects future installs/updates** - it never
retroactively migrates or cleans up instances already on disk.
