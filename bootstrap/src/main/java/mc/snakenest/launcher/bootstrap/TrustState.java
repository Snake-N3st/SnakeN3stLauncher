package mc.snakenest.launcher.bootstrap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import mc.snakenest.launcher.util.AtomicFiles;
import mc.snakenest.launcher.util.Log;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Remembers, per artifact type, that a <em>valid</em> signature has been seen on this installation - the
 * HSTS half of the signature policy.
 *
 * <p>Without it, an attacker would simply remove the signature and fall back to the warning a player can
 * click through, and a warning that has been shown three times for nothing is clicked without reading. With
 * it, the warning exists only while no signature has ever been verified - the transition - and from then on a
 * release that arrives unsigned is a downgrade of the protection itself and is refused.
 *
 * <p>Also keeps the highest version seen per type, used only to <em>warn</em> about a rollback. Refusing older
 * versions would make every legitimate rollback (a bad release withdrawn from the site) a fleet-wide outage,
 * and the signed statement already stops a version from being passed off as another.
 *
 * <p>A small JSON file in the data directory, written atomically. A missing or unreadable file reads as
 * "nothing seen yet" - the safe direction for availability, and someone who can delete files in the player's
 * own data directory can replace the jar outright anyway. Never throws: failing to remember is logged, never a
 * reason to fail an update.
 */
final class TrustState {

    static final String FILE_NAME = "trust-state.json";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Gson target. Type -> highest version seen with a valid signature. */
    private static final class Data {
        Map<String, String> seen = new LinkedHashMap<>();
    }

    private final Path file;
    private Data data;

    private TrustState(Path file, Data data) {
        this.file = file;
        this.data = data;
    }

    /** Keeps nothing, on disk or otherwise: for callers and tests that run without a data directory. */
    static TrustState inMemory() {
        return new TrustState(null, new Data());
    }

    static TrustState load(Path file) {
        Data data = new Data();

        if (Files.isRegularFile(file)) {
            try {
                Data parsed = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), Data.class);

                if (parsed != null && parsed.seen != null) {
                    data = parsed;
                }
            } catch (IOException | RuntimeException e) {
                Log.warn(TrustState.class, "Could not read " + file + ", treating it as empty: " + e.getMessage());
            }
        }

        return new TrustState(file, data);
    }

    synchronized boolean hasSeenValid(String type) {
        return data.seen.containsKey(type);
    }

    synchronized String highestVersion(String type) {
        return data.seen.get(type);
    }

    synchronized void recordValid(String type, String version) {
        String known = data.seen.get(type);

        if (known != null && VersionCompare.compare(version, known) <= 0) {
            return;
        }

        data.seen.put(type, version);
        persist();
    }

    private void persist() {
        if (file == null) {
            return;
        }

        try {
            AtomicFiles.write(file, GSON.toJson(data).getBytes(StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            Log.warn(TrustState.class, "Could not remember that a valid signature was seen (" + file + "): " + e.getMessage());
        }
    }
}
