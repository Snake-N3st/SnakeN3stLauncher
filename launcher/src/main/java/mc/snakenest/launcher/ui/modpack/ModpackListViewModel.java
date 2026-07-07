package mc.snakenest.launcher.ui.modpack;

import mc.snakenest.launcher.modpack.ModpackSummary;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** Data + callbacks for {@link ModpackListPage}. Holds no logic of its own. */
public final class ModpackListViewModel {

    private final List<ModpackSummary> modpacks;
    private final Set<String> installedSlugs;
    private final Map<String, BufferedImage> logos;
    private final Consumer<ModpackSummary> onSelect;
    private final Consumer<ModpackSummary> onQuickAction;

    /**
     * @param onSelect      opens the modpack's detail page (clicking the row itself)
     * @param onQuickAction the card's dedicated play/download icon - installs+launches
     *                      (or installs) right away, without going through the detail
     *                      page first
     */
    public ModpackListViewModel(List<ModpackSummary> modpacks, Set<String> installedSlugs,
                                 Consumer<ModpackSummary> onSelect, Consumer<ModpackSummary> onQuickAction) {
        this(modpacks, installedSlugs, Map.of(), onSelect, onQuickAction);
    }

    /** @param logos pre-fetched modpack logos keyed by slug - see {@code LauncherApp#buildModpackSection} */
    public ModpackListViewModel(List<ModpackSummary> modpacks, Set<String> installedSlugs, Map<String, BufferedImage> logos,
                                 Consumer<ModpackSummary> onSelect, Consumer<ModpackSummary> onQuickAction) {
        this.modpacks = modpacks;
        this.installedSlugs = installedSlugs;
        this.logos = logos;
        this.onSelect = onSelect;
        this.onQuickAction = onQuickAction;
    }

    public List<ModpackSummary> modpacks() {
        return modpacks;
    }

    public boolean isInstalled(ModpackSummary modpack) {
        return installedSlugs.contains(modpack.slug());
    }

    /** {@code null} if none was fetched (best-effort - see {@code ui.common.RemoteImages}). */
    public BufferedImage logoFor(ModpackSummary modpack) {
        return logos.get(modpack.slug());
    }

    public void select(ModpackSummary modpack) {
        onSelect.accept(modpack);
    }

    public void quickAction(ModpackSummary modpack) {
        onQuickAction.accept(modpack);
    }
}
