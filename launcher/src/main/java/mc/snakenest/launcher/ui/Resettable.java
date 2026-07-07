package mc.snakenest.launcher.ui;

/**
 * Implemented by a sidebar page that has its own internal sub-navigation
 * (e.g. {@code ui.modpack.ModpackSectionPage}'s list/detail split) so
 * {@link ContentArea} can put it back to its default view every time the
 * user navigates to it - otherwise switching away (e.g. to "Actualités")
 * and back to "Modpacks" while a modpack's detail page was open left the
 * title reading "Modpacks" but the content still showing that detail page,
 * with no back button to get out of it.
 */
public interface Resettable {

    void resetToDefault();
}
