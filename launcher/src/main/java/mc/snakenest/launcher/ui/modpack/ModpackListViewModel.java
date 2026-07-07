package mc.snakenest.launcher.ui.modpack;

import mc.snakenest.launcher.modpack.ModpackSummary;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** Data + callbacks for {@link ModpackListPage}. Holds no logic of its own. */
public final class ModpackListViewModel {

    private final List<ModpackSummary> modpacks;
    private final Set<String> installedSlugs;
    private final Set<String> updateAvailableSlugs;
    private final Map<String, BufferedImage> logos;
    private final Consumer<ModpackSummary> onSelect;
    private final Consumer<ModpackSummary> onQuickAction;
    private final Runnable onCancel;
    private final Runnable onStop;
    private final Map<String, ModpackCardView> cards = new HashMap<>();

    /**
     * @param onSelect      opens the modpack's detail page (clicking the row itself)
     * @param onQuickAction the card's dedicated play/download icon - installs+launches
     *                      (or installs) right away, without going through the detail
     *                      page first
     */
    public ModpackListViewModel(List<ModpackSummary> modpacks, Set<String> installedSlugs,
                                 Consumer<ModpackSummary> onSelect, Consumer<ModpackSummary> onQuickAction) {
        this(modpacks, installedSlugs, Set.of(), Map.of(), onSelect, onQuickAction, () -> {
        }, () -> {
        });
    }

    /**
     * @param updateAvailableSlugs slugs whose locally-installed version differs from the
     *                              server's latest one - only meaningful for a slug that's
     *                              also in {@code installedSlugs}, drives "Mettre a jour"
     *                              instead of "Demarrer"
     * @param logos     pre-fetched modpack logos keyed by slug - see {@code LauncherApp#buildModpackSection}
     * @param onCancel  cancels whichever quick-installed (or detail-page) sync/install is
     *                  currently in flight - a busy card's icon switches to this
     * @param onStop    stops the currently-running game - a running card's icon switches to this
     */
    public ModpackListViewModel(List<ModpackSummary> modpacks, Set<String> installedSlugs, Set<String> updateAvailableSlugs,
                                 Map<String, BufferedImage> logos, Consumer<ModpackSummary> onSelect, Consumer<ModpackSummary> onQuickAction,
                                 Runnable onCancel, Runnable onStop) {
        this.modpacks = modpacks;
        this.installedSlugs = installedSlugs;
        this.updateAvailableSlugs = updateAvailableSlugs;
        this.logos = logos;
        this.onSelect = onSelect;
        this.onQuickAction = onQuickAction;
        this.onCancel = onCancel;
        this.onStop = onStop;
    }

    public List<ModpackSummary> modpacks() {
        return modpacks;
    }

    public boolean isInstalled(ModpackSummary modpack) {
        return installedSlugs.contains(modpack.slug());
    }

    public boolean isUpdateAvailable(ModpackSummary modpack) {
        return updateAvailableSlugs.contains(modpack.slug());
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

    Runnable onCancel() {
        return onCancel;
    }

    Runnable onStop() {
        return onStop;
    }

    /** Called once per card by {@link ModpackListPage} as it builds the list, so {@code setCard*} below can reach it later. */
    void registerCard(String slug, ModpackCardView card) {
        cards.put(slug, card);
    }

    /**
     * Pushes a busy (sync/install in flight - icon switches to "Annuler") state to the given
     * modpack's card, if this list is still the one currently shown - a no-op otherwise (e.g.
     * the list was refreshed mid-install), same as a stale {@code ModpackDetailPage} reference.
     */
    public void setCardBusy(String slug, boolean busy) {
        ModpackCardView card = cards.get(slug);
        if (card != null) {
            card.setBusy(busy);
        }
    }

    /** Same as {@link #setCardBusy}, for the running-game ("Arrêter") state. */
    public void setCardRunning(String slug, boolean running) {
        ModpackCardView card = cards.get(slug);
        if (card != null) {
            card.setRunning(running);
        }
    }

    /** Flips a card's icon between download and play once install/uninstall finishes. */
    public void setCardInstalled(String slug, boolean installed) {
        ModpackCardView card = cards.get(slug);
        if (card != null) {
            card.setInstalled(installed);
        }
    }

    /** Flips a card's icon between play and "Mettre à jour" once an install/update finishes. */
    public void setCardUpdateAvailable(String slug, boolean updateAvailable) {
        ModpackCardView card = cards.get(slug);
        if (card != null) {
            card.setUpdateAvailable(updateAvailable);
        }
    }
}
