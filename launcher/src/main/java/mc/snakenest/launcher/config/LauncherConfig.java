package mc.snakenest.launcher.config;

/**
 * Persisted user preferences. Plain JSON on disk, never encrypted - so
 * nothing secret belongs here. {@code baseUrl}/{@code clientId} are not
 * stored in this file: they come from the {@code sn3.baseUrl}/
 * {@code sn3.clientId} JVM system properties set by the bootstrap (or by
 * hand during development), read fresh on every launch.
 */
public final class LauncherConfig {

    private Theme theme = Theme.DARK;

    /**
     * The site account id paired with the locally-stored Ed25519 key
     * ({@code crypto.KeyStorage}). Not a secret (see
     * LAUNCHER_INTEGRATION.md: "playerId... peut être stocké normalement"),
     * but every signed request needs it explicitly - the server doesn't
     * derive it from the public key alone - so it must be persisted
     * alongside the key to survive a restart. {@code null} when logged out.
     */
    private Long playerId;

    /** Whether to connect to the local Discord client and show Rich Presence. Default on. */
    private boolean discordEnabled = true;

    /** Where vanilla assets/libraries are stored - see {@link AssetStorageMode}. Shared by default. */
    private AssetStorageMode assetStorageMode = AssetStorageMode.LAUNCHER_SHARED;

    /** What the launcher window does when a game launches - see {@link LaunchWindowAction}. */
    private LaunchWindowAction launchWindowAction = LaunchWindowAction.STAY_OPEN;

    /**
     * The distribution channel this installation follows for the launcher jar itself (a channel
     * name, e.g. "beta") - {@code null} means "use the site's default channel". Read directly
     * from this same config file by the bootstrap on its next run (see
     * {@code bootstrap.DeviceAuthClient}'s sibling channel-reading code) to decide which channel
     * to request when fetching the next launcher jar - the running launcher never fetches its
     * own jar, only the bootstrap does, so this field's only consumer besides the Settings page
     * that writes it is the bootstrap reading it back on the *next* startup.
     */
    private String channel;

    /**
     * The {@code java} binary modpacks are launched with by default - {@code null}/blank means the
     * launcher's own JVM, which is what happened before this setting existed (see
     * {@code game.JavaRuntimes}). A modpack that sets its own overrides this one.
     */
    private String defaultJavaPath;

    public Theme theme() {
        return theme;
    }

    public void setTheme(Theme theme) {
        this.theme = theme;
    }

    public Long playerId() {
        return playerId;
    }

    public void setPlayerId(Long playerId) {
        this.playerId = playerId;
    }

    public boolean discordEnabled() {
        return discordEnabled;
    }

    public void setDiscordEnabled(boolean discordEnabled) {
        this.discordEnabled = discordEnabled;
    }

    public AssetStorageMode assetStorageMode() {
        return assetStorageMode;
    }

    public void setAssetStorageMode(AssetStorageMode assetStorageMode) {
        this.assetStorageMode = assetStorageMode;
    }

    public LaunchWindowAction launchWindowAction() {
        return launchWindowAction;
    }

    public void setLaunchWindowAction(LaunchWindowAction launchWindowAction) {
        this.launchWindowAction = launchWindowAction;
    }

    /** {@code null} means "use the site's default channel". */
    public String defaultJavaPath() {
        return defaultJavaPath;
    }

    public void setDefaultJavaPath(String defaultJavaPath) {
        this.defaultJavaPath = defaultJavaPath;
    }

    public String channel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }
}
