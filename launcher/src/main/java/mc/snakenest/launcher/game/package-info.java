/**
 * Minecraft installation and launch, behind two small interfaces
 * ({@link mc.snakenest.launcher.game.GameInstallService},
 * {@link mc.snakenest.launcher.game.GameLaunchService}) so the rest of the
 * app never depends on the third-party libraries doing the real work - see
 * the {@code flowupdater}/{@code openlauncherlib} sub-packages.
 * {@link mc.snakenest.launcher.game.ModLoader} is shared with
 * {@code modpack} (manifest parsing).
 */
package mc.snakenest.launcher.game;
