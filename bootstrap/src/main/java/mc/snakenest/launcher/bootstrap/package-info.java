/**
 * The entire bootstrap module: check the site for the latest launcher
 * release, download + verify it if needed, spawn it as a new process, exit.
 * See the module's {@code pom.xml} and the top-level plan for why this is a
 * separate artifact from {@code launcher}. {@link mc.snakenest.launcher.bootstrap.BootstrapSplash}
 * shows a small "loading..." popup for however long that takes (a nice-to-have
 * that degrades to a no-op in a headless environment, never a hard
 * dependency of the actual update/handoff logic).
 */
package mc.snakenest.launcher.bootstrap;
