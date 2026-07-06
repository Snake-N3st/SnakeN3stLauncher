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
}
