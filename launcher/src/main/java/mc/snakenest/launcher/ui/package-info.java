/**
 * The Swing shell: {@link mc.snakenest.launcher.ui.LauncherFrame} (logo +
 * top bar + sidebar + page content), {@link mc.snakenest.launcher.ui.ThemeController}
 * (FlatLaf light/dark), and {@link mc.snakenest.launcher.ui.NavTarget}, the
 * shared vocabulary between the sidebar and the content area. Concrete
 * pages live in the sibling {@code ui.modpack}/{@code ui.news}/
 * {@code ui.settings}/{@code ui.account} packages and are handed to the
 * frame from the composition root ({@code Main}), so this package never
 * depends on them.
 */
package mc.snakenest.launcher.ui;
