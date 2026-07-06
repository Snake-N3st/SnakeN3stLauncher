/**
 * Modpack pages: list ({@link mc.snakenest.launcher.ui.modpack.ModpackListPage})
 * and detail ({@link mc.snakenest.launcher.ui.modpack.ModpackDetailPage}),
 * held together by {@link mc.snakenest.launcher.ui.modpack.ModpackSectionPage}
 * (the component actually registered under {@code NavTarget.MODPACKS}).
 * View-models are plain data + callbacks, no logic of their own - the real
 * network/sync/install wiring lives in {@code Main}.
 */
package mc.snakenest.launcher.ui.modpack;
