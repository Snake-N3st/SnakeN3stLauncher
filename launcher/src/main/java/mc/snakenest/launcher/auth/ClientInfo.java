package mc.snakenest.launcher.auth;

import com.google.gson.annotations.SerializedName;

/**
 * A launcher client's public branding: name, optional logo URL, and an
 * optional Discord Rich Presence application ID. Fetched before any login
 * happens (unsigned endpoint, see {@code LauncherAuthApiClient#fetchClientInfo})
 * so the login window itself can show the right name/logo.
 *
 * @param discordAppId the Discord "Application ID" configured for this
 *                     client in {@code /admin/launcher-auth} - {@code null}
 *                     if unset, in which case Discord Rich Presence is
 *                     simply not enabled (see {@code discord.DiscordPresenceService}).
 */
public record ClientInfo(String name, String image, @SerializedName("discord_app_id") String discordAppId) {
}
