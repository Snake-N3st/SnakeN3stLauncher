package mc.snakenest.launcher.modpack;

/** One file entry of a version's manifest: a path, its SHA-256 content hash, and its size. */
public record ManifestFile(String path, String hash, long size) {
}
