package mc.snakenest.launcher.game;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import mc.snakenest.launcher.modpack.LocalManifestStore;
import mc.snakenest.launcher.modpack.ManifestFile;
import mc.snakenest.launcher.modpack.ModpackManifest;
import mc.snakenest.launcher.modpack.StoredManifest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Migration for instances installed before the full launch manifest was cached. Reads disk only. */
public final class LocalInstalledManifestRecovery {
    private LocalInstalledManifestRecovery() { }

    public static Optional<ModpackManifest> recover(Path instance) throws IOException {
        if (!Files.isRegularFile(instance.resolve("client.jar"), LinkOption.NOFOLLOW_LINKS)) return Optional.empty();
        Optional<StoredManifest> stored = new LocalManifestStore().load(instance);
        if (stored.isEmpty()) return Optional.empty();
        List<Path> jsons;
        try (var files = Files.list(instance)) {
            jsons = files.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparingLong(LocalInstalledManifestRecovery::modified).reversed()).toList();
        }
        String activeMinecraft = null;
        String clientHash = null;
        for (Path file : jsons) {
            try {
                if (Files.size(file) > 8 * 1024 * 1024) continue;
                JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                String id = text(json, "id");
                if (id == null || !file.getFileName().toString().equals(id + ".json")
                        || json.has("inheritsFrom") || !json.has("mainClass") || !json.has("libraries")) continue;
                if (activeMinecraft == null) activeMinecraft = id;
                if (json.has("downloads") && json.getAsJsonObject("downloads").has("client")) {
                    String expected = text(json.getAsJsonObject("downloads").getAsJsonObject("client"), "sha1");
                    if (expected != null) {
                        if (clientHash == null) clientHash = clientSha1(instance.resolve("client.jar"));
                        if (expected.equalsIgnoreCase(clientHash)) { activeMinecraft = id; break; }
                    }
                }
            } catch (RuntimeException ignored) { }
        }
        if (activeMinecraft == null) return Optional.empty();
        // FlowUpdater can rewrite vanilla JSON after the loader JSON. Prefer its matching loader,
        // not simply whichever JSON happened to get the newest filesystem timestamp.
        jsons = jsons.stream().sorted(Comparator.comparing((Path path) -> {
            String name = path.getFileName().toString();
            return !(name.startsWith("fabric-loader-") || name.contains("-forge-") || name.startsWith("neoforge-"));
        })).toList();
        for (Path file : jsons) {
            try {
                if (Files.size(file) > 8 * 1024 * 1024) continue;
                JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                if (!json.has("mainClass") || !json.has("libraries")) continue;
                String filename = file.getFileName().toString();
                String loader = "vanilla", loaderVersion = null, mc;
                if (filename.startsWith("fabric-loader-")) {
                    loader = "fabric";
                    mc = text(json, "inheritsFrom");
                    if (mc == null) continue;
                    String suffix = "-" + mc + ".json";
                    if (!filename.endsWith(suffix)) continue;
                    loaderVersion = filename.substring("fabric-loader-".length(), filename.length() - suffix.length());
                } else if (filename.contains("-forge-")) {
                    loader = "forge"; int split = filename.indexOf("-forge-");
                    mc = filename.substring(0, split); loaderVersion = filename.substring(split + 7, filename.length() - 5);
                } else if (filename.startsWith("neoforge-")) {
                    loader = "neoforge"; mc = text(json, "inheritsFrom");
                    loaderVersion = filename.substring(9, filename.length() - 5);
                    if (mc == null) continue;
                } else {
                    mc = text(json, "id");
                    if (mc == null || !filename.equals(mc + ".json") || json.has("inheritsFrom")) continue;
                }
                if (!mc.equals(activeMinecraft)) continue;
                if (!mc.matches("[A-Za-z0-9_.+-]+") || !Files.isRegularFile(instance.resolve(mc + ".json"))) continue;
                List<ManifestFile> manifestFiles = new ArrayList<>();
                if (stored.get().pathToHash() != null) stored.get().pathToHash().forEach((path, hash) ->
                        manifestFiles.add(new ManifestFile(path, hash, 0, null)));
                return Optional.of(new ModpackManifest(stored.get().version(), null, null, 0, mc, loader,
                        loaderVersion, manifestFiles, null, null));
            } catch (RuntimeException ignored) { /* Not a game version JSON. */ }
        }
        return Optional.empty();
    }
    private static String clientSha1(Path file) throws IOException {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-1");
            try (var input = Files.newInputStream(file)) {
                byte[] buffer = new byte[65536]; int count;
                while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
            }
            return mc.snakenest.launcher.util.Hex.encode(digest.digest());
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }

    private static String text(JsonObject json, String field) {
        return json.has(field) && json.get(field).isJsonPrimitive() ? json.get(field).getAsString() : null;
    }
    private static long modified(Path path) {
        try { return Files.getLastModifiedTime(path).toMillis(); } catch (IOException e) { return 0; }
    }
}
