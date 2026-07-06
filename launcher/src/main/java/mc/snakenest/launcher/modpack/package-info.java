/**
 * Modpack listing, manifest fetching, and the hash-diff sync engine that
 * keeps an instance directory up to date with the least possible download,
 * per {@code LAUNCHER_INTEGRATION.md} section 7 in the sibling
 * {@code SnakeN3stLogin} repo. {@link mc.snakenest.launcher.modpack.ManifestDiffer}
 * is pure logic and the most heavily unit-tested class in this package;
 * everything else here does real file/network I/O.
 */
package mc.snakenest.launcher.modpack;
