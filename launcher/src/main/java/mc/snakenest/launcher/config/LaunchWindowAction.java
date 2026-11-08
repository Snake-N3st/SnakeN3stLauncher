package mc.snakenest.launcher.config;

/**
 * What the launcher window does when a modpack's game process starts - the "Action au lancement"
 * setting. Global, not per-modpack (deliberate choice): applies the same way regardless of which
 * modpack is launched. Multiple modpacks can run in parallel (see
 * {@code modpack.InstanceStateRegistry}) - {@code LauncherApp} only actually re-shows or closes the
 * window once every tracked instance has exited, never on the first one to finish while another is
 * still running.
 */
public enum LaunchWindowAction {
    /** Nothing happens - the launcher window stays visible the whole time. Default. */
    STAY_OPEN,

    /** Hides the window when a game starts, shows it again once every running instance has exited. */
    HIDE_AND_RESHOW,

    /** Hides the window when a game starts, and actually closes the launcher once every running
     *  instance has exited (never while any modpack is still running). */
    CLOSE_ON_LAUNCH
}
