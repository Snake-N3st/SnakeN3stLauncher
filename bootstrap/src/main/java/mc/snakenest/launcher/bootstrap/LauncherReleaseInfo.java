package mc.snakenest.launcher.bootstrap;

import com.google.gson.annotations.SerializedName;

/** Mirrors {@code GET /api/launcher-auth/releases/latest} - see LAUNCHER_INTEGRATION.md section 8. */
public record LauncherReleaseInfo(
        String version,
        String sha256,
        long size,
        String changelog,
        @SerializedName("download_url") String downloadUrl
) {
}
