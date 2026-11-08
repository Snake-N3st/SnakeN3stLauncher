package mc.snakenest.launcher.game.openlauncherlib;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import mc.snakenest.launcher.game.LaunchRequest;
import mc.snakenest.launcher.game.ModLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Local-only preflight. Missing game files produce an error; nothing is downloaded or repaired. */
final class LocalLaunchFiles {
    private LocalLaunchFiles() { }

    static void validate(LaunchRequest request) throws IOException {
        if (request.mcVersion() == null || !request.mcVersion().matches("[A-Za-z0-9_.+-]+")
                || request.loader() != ModLoader.VANILLA && (request.loaderVersion() == null || !request.loaderVersion().matches("[A-Za-z0-9_.+-]+")))
            throw new IOException("Version locale invalide");
        Path root = request.instanceDir().toAbsolutePath().normalize();
        require(root.resolve("client.jar"));
        JsonObject vanilla = read(root.resolve(request.mcVersion() + ".json"));
        libraries(root, vanilla);
        String loaderJson = switch (request.loader()) {
            case VANILLA -> null;
            case FABRIC -> "fabric-loader-" + request.loaderVersion() + "-" + request.mcVersion() + ".json";
            case FORGE -> request.mcVersion() + "-forge-" + request.loaderVersion() + ".json";
            case NEOFORGE -> "neoforge-" + request.loaderVersion() + ".json";
            case UNKNOWN -> throw new IOException("Loader local inconnu");
        };
        if (loaderJson != null) libraries(root, read(root.resolve(loaderJson)));
        if (vanilla.has("assetIndex")) {
            String id = vanilla.getAsJsonObject("assetIndex").get("id").getAsString();
            Path index = bounded(root.resolve("assets/indexes"), id + ".json");
            JsonObject assets = read(index);
            if (assets.has("objects")) for (var entry : assets.getAsJsonObject("objects").entrySet()) {
                String hash = entry.getValue().getAsJsonObject().get("hash").getAsString();
                if (!hash.matches("[0-9a-fA-F]{40}")) throw new IOException("Index des ressources local invalide");
                require(root.resolve("assets/objects").resolve(hash.substring(0, 2)).resolve(hash));
            }
        }
    }
    private static void libraries(Path root, JsonObject json) throws IOException {
        if (!json.has("libraries")) return;
        for (JsonElement value : json.getAsJsonArray("libraries")) {
            JsonObject library = value.getAsJsonObject();
            if (!allowed(library)) continue;
            String path;
            if (library.has("downloads")) {
                JsonObject downloads = library.getAsJsonObject("downloads");
                if (!downloads.has("artifact")) continue;
                path = downloads.getAsJsonObject("artifact").get("path").getAsString();
            } else {
                String[] name = library.get("name").getAsString().split(":");
                if (name.length < 3) throw new IOException("Bibliothèque locale invalide");
                path = name[0].replace('.', '/') + "/" + name[1] + "/" + name[2] + "/" + name[1] + "-" + name[2] + ".jar";
            }
            require(bounded(root.resolve("libraries"), path));
        }
    }
    private static boolean allowed(JsonObject library) {
        if (!library.has("rules")) return true;
        boolean allowed = false;
        String platform = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String current = platform.contains("win") ? "windows" : platform.contains("mac") ? "osx" : "linux";
        for (JsonElement value : library.getAsJsonArray("rules")) {
            JsonObject rule = value.getAsJsonObject();
            if (rule.has("features")) continue;
            if (rule.has("os")) {
                JsonObject os = rule.getAsJsonObject("os");
                if (os.has("name") && !current.equals(os.get("name").getAsString())) continue;
                if (os.has("arch") && !System.getProperty("os.arch", "").matches(os.get("arch").getAsString())) continue;
                if (os.has("version") && !System.getProperty("os.version", "").matches(os.get("version").getAsString())) continue;
            }
            allowed = "allow".equals(rule.get("action").getAsString());
        }
        return allowed;
    }
    private static Path bounded(Path root, String relative) throws IOException {
        Path normalized = root.toAbsolutePath().normalize(), file = normalized.resolve(relative).normalize();
        if (!file.startsWith(normalized) || file.equals(normalized)) throw new IOException("Chemin local invalide");
        return file;
    }
    private static JsonObject read(Path file) throws IOException {
        require(file);
        try { return JsonParser.parseString(Files.readString(file)).getAsJsonObject(); }
        catch (RuntimeException e) { throw new IOException("Métadonnées locales invalides : " + file.getFileName(), e); }
    }
    private static void require(Path file) throws IOException {
        if (!Files.isRegularFile(file) || Files.size(file) == 0)
            throw new IOException("Fichier local manquant : " + file.getFileName() + ". Reconnecte-toi pour réparer cette installation.");
    }
}
