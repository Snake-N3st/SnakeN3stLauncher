package mc.snakenest.launcher.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import mc.snakenest.launcher.util.AppDirs;
import mc.snakenest.launcher.util.AtomicFiles;
import mc.snakenest.launcher.util.Log;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Loads/saves {@link LauncherConfig} as JSON at {@link AppDirs#configFile()}. */
public final class ConfigStore {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private final Path configFile;

    public ConfigStore(AppDirs dirs) {
        this.configFile = dirs.configFile();
    }

    /** Returns a fresh default config if the file is missing or unreadable. */
    public LauncherConfig load() {
        if (!Files.isRegularFile(configFile)) {
            return new LauncherConfig();
        }
        try {
            String json = Files.readString(configFile, StandardCharsets.UTF_8);
            LauncherConfig config = GSON.fromJson(json, LauncherConfig.class);
            return config != null ? config : new LauncherConfig();
        } catch (IOException | JsonSyntaxException e) {
            Log.warn(ConfigStore.class, "Could not read config.json, falling back to defaults: " + e.getMessage());
            return new LauncherConfig();
        }
    }

    public void save(LauncherConfig config) {
        try {
            AtomicFiles.write(configFile, GSON.toJson(config).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            Log.warn(ConfigStore.class, "Could not save config.json: " + e.getMessage());
        }
    }
}
