package mc.snakenest.launcher.ui.modpack;

import mc.snakenest.launcher.modpack.ModpackSummary;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/** Data + callbacks for {@link ModpackListPage}. Holds no logic of its own. */
public final class ModpackListViewModel {

    private final List<ModpackSummary> modpacks;
    private final Set<String> installedSlugs;
    private final Consumer<ModpackSummary> onSelect;

    public ModpackListViewModel(List<ModpackSummary> modpacks, Set<String> installedSlugs, Consumer<ModpackSummary> onSelect) {
        this.modpacks = modpacks;
        this.installedSlugs = installedSlugs;
        this.onSelect = onSelect;
    }

    public List<ModpackSummary> modpacks() {
        return modpacks;
    }

    public boolean isInstalled(ModpackSummary modpack) {
        return installedSlugs.contains(modpack.slug());
    }

    public void select(ModpackSummary modpack) {
        onSelect.accept(modpack);
    }
}
