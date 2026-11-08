package mc.snakenest.launcher.ui.modpack;

import mc.snakenest.launcher.game.ModLoader;
import mc.snakenest.launcher.modpack.ModMeta;
import mc.snakenest.launcher.modpack.ModpackManifest;
import mc.snakenest.launcher.modpack.ModsMetaStore;
import mc.snakenest.launcher.modrinth.ModrinthApiClient;
import mc.snakenest.launcher.modrinth.ModrinthProjectSummary;
import mc.snakenest.launcher.modrinth.ModrinthSearchHit;
import mc.snakenest.launcher.modrinth.ModrinthVersion;
import mc.snakenest.launcher.modrinth.ModrinthVersionFile;
import mc.snakenest.launcher.net.HttpJsonClient;
import mc.snakenest.launcher.net.RawResponse;
import mc.snakenest.launcher.ui.common.AvatarPanel;
import mc.snakenest.launcher.ui.common.Borders;
import mc.snakenest.launcher.ui.common.Buttons;
import mc.snakenest.launcher.ui.common.Colors;
import mc.snakenest.launcher.ui.common.Icons;
import mc.snakenest.launcher.ui.common.RemoteImages;
import mc.snakenest.launcher.util.Hex;
import mc.snakenest.launcher.util.Log;
import mc.snakenest.launcher.util.Sha1;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * "Gérer les mods" from a modpack's options menu (see {@code ModpackDetailPage#showSettingsMenu}):
 * search Modrinth, install/switch-version/remove by hash-identified file, import a local
 * {@code .jar}, open the {@code mods/} folder directly. Three sub-pages behind an internal
 * {@link CardLayout} - same "list, then a dedicated detail view" shape as
 * {@code ModpackSectionPage}, extended with a third page for version selection since a version
 * list crammed into a side panel didn't leave enough room to read compatibility at a glance:
 *
 * <ul>
 *   <li><b>Recherche</b> - {@link ModCardView} cards (logo, name, author, description), compatible
 *   mods first, incompatible ones greyed out and pushed to the bottom rather than hidden.</li>
 *   <li><b>Détail</b> - the selected mod's logo/author/description, a "Voir sur Modrinth" link,
 *   "Installation rapide" (latest compatible version), "Choisir une version", "Retirer".</li>
 *   <li><b>Versions</b> - every published version ({@link ModVersionRowView} rows, compatible
 *   first), each with its own Installer/Retirer action.</li>
 * </ul>
 *
 * <p>Every network/disk operation runs on the caller's {@code ExecutorService}, never the EDT;
 * results are marshalled back via {@code SwingUtilities.invokeLater}. Monotonic generation
 * counters on both the search and the per-mod version fetch discard a slow response that arrives
 * after a newer request has already superseded it.
 */
public final class ModManagerDialog {

    private static final String PAGE_SEARCH = "search";
    private static final String PAGE_DETAIL = "detail";
    private static final String PAGE_VERSIONS = "versions";

    private final JDialog dialog;
    private final Path instanceDir;
    private final Path modsDir;
    private final ExecutorService executor;
    private final HttpJsonClient http = new HttpJsonClient();
    private final ModrinthApiClient modrinthApi = new ModrinthApiClient(http);
    private final ModsMetaStore metaStore = new ModsMetaStore();
    private final String mcVersion;
    private final String loaderKeyword;

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cardPanel = new JPanel(cardLayout);
    private final mc.snakenest.launcher.ui.common.VerticalListPanel resultsListPanel = new mc.snakenest.launcher.ui.common.VerticalListPanel();
    private final mc.snakenest.launcher.ui.common.VerticalListPanel versionsListPanel = new mc.snakenest.launcher.ui.common.VerticalListPanel();
    private final JLabel statusLabel = new JLabel(" ");
    private final JTextField searchField = new JTextField();

    private final AvatarPanel detailAvatar = new AvatarPanel("?", 72);
    private final JLabel detailTitleLabel = new JLabel(" ");
    private final JLabel detailAuthorLabel = new JLabel(" ");
    private final JTextArea detailDescriptionArea = new JTextArea();
    private final JButton detailQuickInstallButton = new JButton("Installation rapide");
    private final JButton detailChooseVersionButton = new JButton("Choisir une version");
    private final JButton detailRemoveButton = new JButton("Retirer");
    private final JLabel versionsTitleLabel = new JLabel(" ");

    private Map<String, ModMeta> meta = new LinkedHashMap<>();
    private Map<String, String> installedByProject = new LinkedHashMap<>();
    // Cleared/repopulated on every renderResults() - only ever used to reach a row currently on
    // screen (spinner during its own quick-install, update-available badge once that async check
    // resolves), never to persist state across a re-render.
    private final Map<String, ModCardView> visibleCards = new LinkedHashMap<>();
    private int searchGeneration;
    private int versionsGeneration;
    private ModrinthSearchHit currentHit;
    private List<ModrinthVersion> currentVersions = List.of();

    private ModManagerDialog(Component parent, ModpackManifest manifest, Path instanceDir, ExecutorService executor) {
        this.instanceDir = instanceDir;
        this.modsDir = instanceDir.resolve("mods");
        this.executor = executor;
        this.mcVersion = manifest.mcVersion();
        this.loaderKeyword = loaderKeyword(manifest.modLoader());

        dialog = new JDialog(SwingUtilities.getWindowAncestor(parent), "Gérer les mods", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setLayout(new BorderLayout());

        cardPanel.add(buildSearchPage(), PAGE_SEARCH);
        cardPanel.add(buildDetailPage(), PAGE_DETAIL);
        cardPanel.add(buildVersionsPage(), PAGE_VERSIONS);
        dialog.add(cardPanel, BorderLayout.CENTER);
        dialog.add(buildBottomBar(), BorderLayout.SOUTH);

        dialog.setPreferredSize(new Dimension(760, 620));
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
    }

    public static void show(Component parent, ModpackManifest manifest, Path instanceDir, ExecutorService executor) {
        ModManagerDialog managerDialog = new ModManagerDialog(parent, manifest, instanceDir, executor);
        managerDialog.executor.submit(managerDialog::loadInitialState);
        managerDialog.dialog.setVisible(true);
    }

    // === Page: Recherche =================================================

    private JPanel buildSearchPage() {
        JPanel page = new JPanel(new BorderLayout(0, 10));
        page.setBorder(new EmptyBorder(12, 12, 0, 12));

        JPanel searchRow = new JPanel(new BorderLayout(6, 0));
        // FlatLaf-specific client property (already the L&F in use here) - so players searching
        // for a mod know upfront the results come from Modrinth's own catalog, not something
        // curated by this modpack/site.
        searchField.putClientProperty("JTextField.placeholderText", "Rechercher sur Modrinth");
        searchField.addActionListener(e -> doSearch());
        searchRow.add(searchField, BorderLayout.CENTER);
        JButton searchButton = new JButton("Rechercher");
        searchButton.addActionListener(e -> doSearch());
        searchRow.add(searchButton, BorderLayout.EAST);
        page.add(searchRow, BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane(resultsListPanel);
        scroll.setBorder(Borders.none());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        page.add(scroll, BorderLayout.CENTER);

        return page;
    }

    private void doSearch() {
        String query = searchField.getText().trim();
        if (query.isEmpty()) {
            return;
        }
        int generation = ++searchGeneration;
        setStatus("Recherche en cours...");
        executor.submit(() -> {
            try {
                List<ModrinthSearchHit> hits = modrinthApi.search(query, 40);
                List<ModrinthSearchHit> sorted = hits.stream()
                        .sorted(Comparator.comparing(h -> !h.isCompatible(mcVersion, loaderKeyword)))
                        .toList();
                SwingUtilities.invokeLater(() -> {
                    if (generation != searchGeneration) {
                        return;
                    }
                    renderResults(sorted);
                    statusLabel.setForeground(UIManager.getColor("Label.foreground"));
                    statusLabel.setText(sorted.isEmpty() ? "Aucun résultat." : sorted.size() + " résultat(s).");
                });
            } catch (Exception e) {
                Log.warn(ModManagerDialog.class, "Modrinth search failed for \"" + query + "\": " + e.getMessage());
                if (generation == searchGeneration) {
                    setError("Erreur de recherche : " + e.getMessage());
                }
            }
        });
    }

    private void renderResults(List<ModrinthSearchHit> hits) {
        resultsListPanel.removeAll();
        visibleCards.clear();
        for (ModrinthSearchHit hit : hits) {
            boolean compatible = hit.isCompatible(mcVersion, loaderKeyword);
            boolean installed = installedByProject.containsKey(hit.projectId());
            ModCardView card = new ModCardView(hit, compatible, installed, () -> openDetail(hit), () -> quickInstall(hit));
            card.setAlignmentX(Component.LEFT_ALIGNMENT);
            card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 84));
            visibleCards.put(hit.projectId(), card);
            resultsListPanel.add(card);
            resultsListPanel.add(Box.createVerticalStrut(10));
            executor.submit(() -> {
                var icon = RemoteImages.tryLoad(hit.iconUrl());
                if (icon != null) {
                    card.setIcon(icon);
                }
            });
            if (installed) {
                checkForUpdate(hit, card);
            }
        }
        resultsListPanel.revalidate();
        resultsListPanel.repaint();
    }

    /**
     * The quick-install button is hidden for an already-installed mod by default (see
     * {@link ModCardView}'s own Javadoc) - this is what decides whether it should actually be
     * shown after all, because a newer compatible version exists. Bounded to installed rows
     * only, never all 40 visible results, to avoid a version-list fetch per row.
     */
    private void checkForUpdate(ModrinthSearchHit hit, ModCardView card) {
        String installedPath = installedByProject.get(hit.projectId());
        String installedVersionId = installedPath != null && meta.containsKey(installedPath)
                ? meta.get(installedPath).modrinthVersionId() : null;
        if (installedVersionId == null) {
            return;
        }
        executor.submit(() -> {
            try {
                ModrinthVersion best = bestCompatibleVersion(modrinthApi.versions(hit.projectId()));
                if (best != null && !best.id().equals(installedVersionId)) {
                    card.setUpdateAvailable(true);
                }
            } catch (Exception e) {
                Log.warn(ModManagerDialog.class, "Could not check for updates for " + hit.projectId() + ": " + e.getMessage());
            }
        });
    }

    private void quickInstall(ModrinthSearchHit hit) {
        ModCardView card = visibleCards.get(hit.projectId());
        AtomicBoolean cancelled = new AtomicBoolean(false);
        if (card != null) {
            card.setOnCancel(() -> cancelled.set(true));
            card.setInstalling(true);
        }
        setStatus("Installation de \"" + hit.title() + "\"...");
        executor.submit(() -> {
            try {
                ModrinthVersion best = bestCompatibleVersion(modrinthApi.versions(hit.projectId()));
                if (best == null) {
                    setError("Aucune version compatible pour \"" + hit.title() + "\".");
                    return;
                }
                long totalSize = best.primaryFile().map(ModrinthVersionFile::size).orElse(0L);
                boolean success = installVersion(hit.projectId(), hit.title(), best,
                        bytesWritten -> {
                            if (card != null) {
                                card.setDownloadProgress(totalSize > 0 ? (double) bytesWritten / totalSize : -1);
                            }
                        },
                        cancelled::get);
                if (success && card != null) {
                    card.setInstalled(true);
                }
            } catch (Exception e) {
                Log.warn(ModManagerDialog.class, "Quick install failed for " + hit.projectId() + ": " + e.getMessage());
                setError("Échec de l'installation : " + e.getMessage());
            } finally {
                if (card != null) {
                    card.setInstalling(false);
                }
            }
        });
    }

    /**
     * The best version to quick-install/update to - or {@code null} if none of this project's
     * versions are actually compatible with {@code mcVersion}/{@code loaderKeyword}. Used to
     * silently fall back to the newest version regardless of compatibility when none matched,
     * which meant every quick-install path (the card button, the detail page's button, and even
     * the required-dependency auto-install) could end up installing a mod build that doesn't
     * actually work with this modpack - {@code installMissingDependencies}'s own null handling
     * ("Aucune version compatible pour...") existed already but could never actually fire because
     * of that fallback. Never silently installing an incompatible version this way is exactly
     * what backs {@link ModCardView}'s "Incompatible" badge/hidden quick-install button too.
     */
    private ModrinthVersion bestCompatibleVersion(List<ModrinthVersion> versions) {
        return versions.stream()
                .filter(v -> v.isCompatible(mcVersion, loaderKeyword))
                .findFirst()
                .orElse(null);
    }

    // === Page: Détail =====================================================

    private JPanel buildDetailPage() {
        JPanel page = new JPanel(new BorderLayout(0, 12));
        page.setBorder(new EmptyBorder(12, 12, 0, 12));

        JPanel backRow = new JPanel(new BorderLayout(8, 0));
        backRow.add(Buttons.flatIcon(Icons.backArrow(20), "Retour", () -> cardLayout.show(cardPanel, PAGE_SEARCH)), BorderLayout.WEST);
        page.add(backRow, BorderLayout.NORTH);

        JPanel header = new JPanel(new BorderLayout(16, 0));
        header.setOpaque(false);
        header.add(detailAvatar, BorderLayout.WEST);

        JPanel headerText = new JPanel();
        headerText.setOpaque(false);
        headerText.setLayout(new BoxLayout(headerText, BoxLayout.Y_AXIS));
        detailTitleLabel.setFont(detailTitleLabel.getFont().deriveFont(Font.BOLD, 20f));
        detailAuthorLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        // Same BoxLayout.Y_AXIS default-centers-each-child fix as ModCardView/ModVersionRowView.
        detailTitleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        detailAuthorLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        headerText.add(Box.createVerticalGlue());
        headerText.add(detailTitleLabel);
        headerText.add(detailAuthorLabel);
        headerText.add(Box.createVerticalGlue());
        header.add(headerText, BorderLayout.CENTER);

        JPanel top = new JPanel(new BorderLayout(0, 16));
        top.setOpaque(false);
        top.add(header, BorderLayout.NORTH);

        detailDescriptionArea.setEditable(false);
        detailDescriptionArea.setFocusable(false);
        detailDescriptionArea.setOpaque(false);
        detailDescriptionArea.setLineWrap(true);
        detailDescriptionArea.setWrapStyleWord(true);
        detailDescriptionArea.setFont(detailDescriptionArea.getFont().deriveFont(14f));
        top.add(new JScrollPane(detailDescriptionArea), BorderLayout.CENTER);

        page.add(top, BorderLayout.CENTER);

        JButton openModrinthButton = new JButton("Voir sur Modrinth");
        openModrinthButton.addActionListener(e -> openModrinthPage());
        detailQuickInstallButton.setEnabled(false);
        detailQuickInstallButton.addActionListener(e -> quickInstallFromDetail());
        detailChooseVersionButton.setEnabled(false);
        detailChooseVersionButton.addActionListener(e -> openVersions());
        detailRemoveButton.setEnabled(false);
        detailRemoveButton.addActionListener(e -> removeCurrentHit());

        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttonRow.setOpaque(false);
        buttonRow.add(openModrinthButton);
        buttonRow.add(detailRemoveButton);
        buttonRow.add(detailChooseVersionButton);
        buttonRow.add(detailQuickInstallButton);
        page.add(buttonRow, BorderLayout.SOUTH);

        return page;
    }

    private void openDetail(ModrinthSearchHit hit) {
        currentHit = hit;
        currentVersions = List.of();
        detailAvatar.setImage(null);
        detailAvatar.setPlaceholderName(hit.title());
        detailTitleLabel.setText(hit.title());
        detailAuthorLabel.setText("par " + hit.author());
        detailDescriptionArea.setText(hit.description());
        detailDescriptionArea.setCaretPosition(0);
        detailQuickInstallButton.setEnabled(false);
        detailChooseVersionButton.setEnabled(false);
        detailRemoveButton.setEnabled(installedByProject.containsKey(hit.projectId()));
        cardLayout.show(cardPanel, PAGE_DETAIL);

        int generation = ++versionsGeneration;
        executor.submit(() -> {
            var icon = RemoteImages.tryLoad(hit.iconUrl());
            SwingUtilities.invokeLater(() -> {
                if (hit.equals(currentHit)) {
                    detailAvatar.setImage(icon);
                }
            });
        });
        executor.submit(() -> {
            try {
                List<ModrinthVersion> versions = modrinthApi.versions(hit.projectId());
                List<ModrinthVersion> sorted = versions.stream()
                        .sorted(Comparator.comparing(v -> !v.isCompatible(mcVersion, loaderKeyword)))
                        .toList();
                SwingUtilities.invokeLater(() -> {
                    if (generation != versionsGeneration || !hit.equals(currentHit)) {
                        return;
                    }
                    currentVersions = sorted;
                    // Unlike "Choisir une version" (a deliberate manual-override path - see
                    // ModCardView's own Javadoc: a player might still want to force-install an
                    // incompatible mod on purpose), quick-install must never offer a version that
                    // doesn't actually work with this modpack - same rule bestCompatibleVersion
                    // itself now enforces.
                    detailQuickInstallButton.setEnabled(sorted.stream().anyMatch(v -> v.isCompatible(mcVersion, loaderKeyword)));
                    detailChooseVersionButton.setEnabled(!sorted.isEmpty());
                });
            } catch (Exception e) {
                Log.warn(ModManagerDialog.class, "Fetching versions failed for " + hit.projectId() + ": " + e.getMessage());
                if (generation == versionsGeneration) {
                    setError("Erreur : " + e.getMessage());
                }
            }
        });
    }

    private void openModrinthPage() {
        if (currentHit == null) {
            return;
        }
        try {
            Desktop.getDesktop().browse(URI.create("https://modrinth.com/mod/" + currentHit.slug()));
        } catch (IOException e) {
            setError("Impossible d'ouvrir le lien : " + e.getMessage());
        }
    }

    private void quickInstallFromDetail() {
        ModrinthSearchHit hit = currentHit;
        if (hit == null) {
            return;
        }
        ModrinthVersion best = bestCompatibleVersion(currentVersions);
        if (best == null) {
            setError("Aucune version compatible pour \"" + hit.title() + "\".");
            return;
        }
        setStatus("Installation de \"" + hit.title() + "\"...");
        executor.submit(() -> installVersion(hit, best));
    }

    private void removeCurrentHit() {
        ModrinthSearchHit hit = currentHit;
        if (hit == null) {
            return;
        }
        executor.submit(() -> removeMod(hit));
    }

    // === Page: Versions ===================================================

    private JPanel buildVersionsPage() {
        JPanel page = new JPanel(new BorderLayout(0, 12));
        page.setBorder(new EmptyBorder(12, 12, 0, 12));

        JPanel backRow = new JPanel(new BorderLayout(8, 0));
        backRow.add(Buttons.flatIcon(Icons.backArrow(20), "Retour", () -> cardLayout.show(cardPanel, PAGE_DETAIL)), BorderLayout.WEST);
        versionsTitleLabel.setFont(versionsTitleLabel.getFont().deriveFont(Font.BOLD, 16f));
        backRow.add(versionsTitleLabel, BorderLayout.CENTER);
        page.add(backRow, BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane(versionsListPanel);
        scroll.setBorder(Borders.none());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        page.add(scroll, BorderLayout.CENTER);

        return page;
    }

    private void openVersions() {
        if (currentHit == null || currentVersions.isEmpty()) {
            return;
        }
        versionsTitleLabel.setText("Versions de " + currentHit.title());
        renderVersionsList();
        cardLayout.show(cardPanel, PAGE_VERSIONS);
    }

    private void renderVersionsList() {
        ModrinthSearchHit hit = currentHit;
        if (hit == null) {
            return;
        }
        String installedPath = installedByProject.get(hit.projectId());
        String installedVersionId = installedPath != null && meta.containsKey(installedPath)
                ? meta.get(installedPath).modrinthVersionId() : null;

        versionsListPanel.removeAll();
        for (ModrinthVersion version : currentVersions) {
            boolean compatible = version.isCompatible(mcVersion, loaderKeyword);
            boolean isInstalled = version.id().equals(installedVersionId);
            ModVersionRowView row = new ModVersionRowView(version, compatible, isInstalled, () -> {
                if (isInstalled) {
                    executor.submit(() -> removeMod(hit));
                } else {
                    setStatus("Installation de \"" + hit.title() + "\"...");
                    executor.submit(() -> installVersion(hit, version));
                }
            });
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 64));
            versionsListPanel.add(row);
            versionsListPanel.add(Box.createVerticalStrut(8));
        }
        versionsListPanel.revalidate();
        versionsListPanel.repaint();
    }

    // === Bottom bar (persistent across pages) ============================

    private JPanel buildBottomBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBorder(new EmptyBorder(8, 12, 12, 12));

        statusLabel.setBorder(new EmptyBorder(0, 0, 8, 0));
        bar.add(statusLabel, BorderLayout.NORTH);

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.add(Buttons.iconButton(Icons.folder(22), "Ouvrir le dossier des mods", this::openModsFolder));
        JButton importButton = new JButton("Importer un .jar");
        importButton.addActionListener(e -> importJar());
        left.add(importButton);
        bar.add(left, BorderLayout.WEST);

        JButton close = new JButton("Fermer");
        close.addActionListener(e -> dialog.dispose());
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        right.add(close);
        bar.add(right, BorderLayout.EAST);

        return bar;
    }

    // === Background operations ===========================================

    private void loadInitialState() {
        try {
            meta = metaStore.load(instanceDir);
        } catch (IOException e) {
            Log.warn(ModManagerDialog.class, "Corrupt mods metadata, resetting: " + e.getMessage());
            meta = new LinkedHashMap<>();
        }
        discoverUntrackedMods();
        refreshInstalledMap();
        browsePopular();
    }

    /** Fills the search page with the best-known mods before the player has typed anything, rather than leaving it empty. */
    private void browsePopular() {
        try {
            List<ModrinthSearchHit> hits = modrinthApi.browsePopular(40);
            List<ModrinthSearchHit> sorted = hits.stream()
                    .sorted(Comparator.comparing(h -> !h.isCompatible(mcVersion, loaderKeyword)))
                    .toList();
            SwingUtilities.invokeLater(() -> {
                if (searchGeneration != 0) {
                    return;
                }
                renderResults(sorted);
                statusLabel.setForeground(UIManager.getColor("Label.foreground"));
                statusLabel.setText("Mods les plus populaires.");
            });
        } catch (Exception e) {
            Log.warn(ModManagerDialog.class, "Could not load popular mods: " + e.getMessage());
        }
    }

    /** Identifies mod files already in {@code mods/} that this launcher has never seen before (e.g. dropped in by hand). */
    private void discoverUntrackedMods() {
        if (!Files.isDirectory(modsDir)) {
            return;
        }
        Map<String, String> hashByFile = new LinkedHashMap<>();
        try (var files = Files.list(modsDir)) {
            for (Path file : (Iterable<Path>) files::iterator) {
                String name = file.getFileName().toString();
                if (!Files.isRegularFile(file) || !name.endsWith(".jar") || meta.containsKey(name)) {
                    continue;
                }
                try (InputStream in = Files.newInputStream(file)) {
                    hashByFile.put(name, Sha1.hex(in));
                }
            }
        } catch (IOException e) {
            Log.warn(ModManagerDialog.class, "Could not scan " + modsDir + ": " + e.getMessage());
            return;
        }
        if (hashByFile.isEmpty()) {
            return;
        }
        try {
            Map<String, ModrinthVersion> found = modrinthApi.lookupByHashes(new ArrayList<>(hashByFile.values()));
            for (var entry : hashByFile.entrySet()) {
                ModrinthVersion version = found.get(entry.getValue());
                meta.put(entry.getKey(), new ModMeta(entry.getValue(),
                        version != null ? version.projectId() : null,
                        version != null ? version.id() : null));
            }
            metaStore.save(instanceDir, meta);
        } catch (Exception e) {
            // Best-effort discovery only - an unreachable Modrinth here just means these files
            // stay unidentified until the next time the dialog opens, not a reason to fail.
            Log.warn(ModManagerDialog.class, "Could not identify untracked mods via Modrinth: " + e.getMessage());
        }
    }

    private boolean installVersion(ModrinthSearchHit hit, ModrinthVersion version) {
        return installVersion(hit.projectId(), hit.title(), version);
    }

    private boolean installVersion(String projectId, String title, ModrinthVersion version) {
        return installVersion(projectId, title, version, bytes -> {
        }, () -> false);
    }

    /**
     * Decoupled from {@link ModrinthSearchHit} (only ever used {@code projectId()}/{@code
     * title()} anyway) so {@link #installMissingDependencies} can reuse it for a dependency,
     * which only ever comes with a {@link ModrinthProjectSummary}, not a full search hit.
     *
     * @param onProgress  bytes written so far - only {@link #quickInstall} wires a real one, to
     *                    drive {@code ModCardView}'s progress ring; every other caller passes a
     *                    no-op, they have no per-row UI to update
     * @param isCancelled polled between chunks - only {@link #quickInstall} wires a real one, to
     *                    back its progress ring's hover-to-cancel button
     * @return true on success - {@link #quickInstall} uses this to flip its row straight to
     * "installed" without waiting for the next search to re-render it
     */
    private boolean installVersion(String projectId, String title, ModrinthVersion version,
                                    java.util.function.LongConsumer onProgress,
                                    java.util.function.BooleanSupplier isCancelled) {
        var fileOpt = version.primaryFile();
        if (fileOpt.isEmpty()) {
            setError("Cette version de \"" + title + "\" n'a aucun fichier téléchargeable.");
            return false;
        }
        ModrinthVersionFile file = fileOpt.get();
        try {
            Files.createDirectories(modsDir);
            Path target = modsDir.resolve(file.filename());
            try (RawResponse response = http.getRaw(URI.create(file.url()))) {
                if (!response.isSuccess()) {
                    throw new IOException("HTTP " + response.statusCode());
                }
                writeVerifiedSha1(target, response.body(), file.sha1(), onProgress, isCancelled);
            }

            String previousPath = installedByProject.get(projectId);
            if (previousPath != null && !previousPath.equals(file.filename())) {
                Files.deleteIfExists(modsDir.resolve(previousPath));
                meta.remove(previousPath);
            }
            meta.put(file.filename(), new ModMeta(file.sha1(), projectId, version.id()));
            metaStore.save(instanceDir, meta);
            refreshInstalledMap();
            setStatus("\"" + title + "\" installé (" + version.versionNumber() + ").");
            promptForMissingDependencies(version);
            return true;
        } catch (InstallCancelledException e) {
            setStatus("Installation de \"" + title + "\" annulée.");
            return false;
        } catch (IOException | InterruptedException e) {
            Log.warn(ModManagerDialog.class, "Install failed for " + projectId + ": " + e.getMessage());
            setError("Échec de l'installation : " + e.getMessage());
            return false;
        }
    }

    /**
     * After a successful install, offers to also install whichever {@code required} dependencies
     * (see {@link ModrinthVersion#requiredDependencyProjectIds}) aren't already present - e.g.
     * Iris really does declare Sodium as a required dependency for real, confirmed against the
     * live API before writing this. Best-effort: a dependency whose project info can't be
     * fetched is silently dropped from the prompt rather than failing the whole install that
     * already succeeded.
     */
    private void promptForMissingDependencies(ModrinthVersion version) {
        List<String> missingIds = version.requiredDependencyProjectIds().stream()
                .filter(id -> !installedByProject.containsKey(id))
                .distinct()
                .toList();
        if (missingIds.isEmpty()) {
            return;
        }
        List<ModrinthProjectSummary> missing = new ArrayList<>();
        for (String id : missingIds) {
            try {
                missing.add(modrinthApi.getProject(id));
            } catch (Exception e) {
                Log.warn(ModManagerDialog.class, "Could not fetch dependency project " + id + ": " + e.getMessage());
            }
        }
        if (missing.isEmpty()) {
            return;
        }
        String names = missing.stream().map(ModrinthProjectSummary::title).collect(java.util.stream.Collectors.joining(", "));
        SwingUtilities.invokeLater(() -> {
            int choice = JOptionPane.showConfirmDialog(dialog,
                    "\"" + names + "\" " + (missing.size() > 1 ? "sont requis" : "est requis") + " par ce mod. L'installer ?",
                    "Dépendance requise", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (choice == JOptionPane.YES_OPTION) {
                executor.submit(() -> installMissingDependencies(missing));
            }
        });
    }

    private void installMissingDependencies(List<ModrinthProjectSummary> projects) {
        for (ModrinthProjectSummary project : projects) {
            try {
                ModrinthVersion best = bestCompatibleVersion(modrinthApi.versions(project.id()));
                if (best == null) {
                    setError("Aucune version compatible pour la dépendance \"" + project.title() + "\".");
                    continue;
                }
                installVersion(project.id(), project.title(), best);
            } catch (Exception e) {
                Log.warn(ModManagerDialog.class, "Could not install dependency " + project.id() + ": " + e.getMessage());
                setError("Échec de l'installation de \"" + project.title() + "\" : " + e.getMessage());
            }
        }
    }

    private void removeMod(ModrinthSearchHit hit) {
        String path = installedByProject.get(hit.projectId());
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(modsDir.resolve(path));
            meta.remove(path);
            metaStore.save(instanceDir, meta);
            refreshInstalledMap();
            setStatus("\"" + hit.title() + "\" retiré.");
        } catch (IOException e) {
            Log.warn(ModManagerDialog.class, "Remove failed for " + hit.projectId() + ": " + e.getMessage());
            setError("Échec du retrait : " + e.getMessage());
        }
    }

    private void importJar() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Fichier .jar", "jar"));
        if (chooser.showOpenDialog(dialog) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path source = chooser.getSelectedFile().toPath();
        executor.submit(() -> {
            try {
                Files.createDirectories(modsDir);
                Path target = modsDir.resolve(source.getFileName());
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                String hash;
                try (InputStream in = Files.newInputStream(target)) {
                    hash = Sha1.hex(in);
                }
                meta.put(target.getFileName().toString(), new ModMeta(hash, null, null));
                metaStore.save(instanceDir, meta);
                refreshInstalledMap();
                setStatus("\"" + target.getFileName() + "\" importé.");
            } catch (IOException e) {
                Log.warn(ModManagerDialog.class, "Import failed for " + source + ": " + e.getMessage());
                setError("Échec de l'import : " + e.getMessage());
            }
        });
    }

    private void openModsFolder() {
        try {
            Files.createDirectories(modsDir);
            Desktop.getDesktop().open(modsDir.toFile());
        } catch (IOException e) {
            setError("Impossible d'ouvrir le dossier : " + e.getMessage());
        }
    }

    private void refreshInstalledMap() {
        Map<String, String> byProject = new LinkedHashMap<>();
        for (var entry : meta.entrySet()) {
            ModMeta modMeta = entry.getValue();
            if (modMeta.modrinthProjectId() != null && Files.exists(modsDir.resolve(entry.getKey()))) {
                byProject.put(modMeta.modrinthProjectId(), entry.getKey());
            }
        }
        SwingUtilities.invokeLater(() -> {
            installedByProject = byProject;
            if (currentHit != null) {
                detailRemoveButton.setEnabled(installedByProject.containsKey(currentHit.projectId()));
                if (!currentVersions.isEmpty()) {
                    renderVersionsList();
                }
            }
        });
    }

    private void setStatus(String text) {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setForeground(UIManager.getColor("Label.foreground"));
            statusLabel.setText(text);
        });
    }

    private void setError(String text) {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setForeground(Colors.danger());
            statusLabel.setText(text);
        });
    }

    /** {@code null} for a loader Modrinth has no concept of (vanilla) or one this launcher doesn't recognize. */
    private static String loaderKeyword(ModLoader loader) {
        return switch (loader) {
            case FORGE -> "forge";
            case FABRIC -> "fabric";
            case NEOFORGE -> "neoforge";
            case VANILLA, UNKNOWN -> null;
        };
    }

    private static final int COPY_BUFFER_SIZE = 8192;

    /**
     * Same write-then-verify-then-atomically-move shape as {@code util.AtomicFiles.writeVerified},
     * but SHA-1 (Modrinth's own hashing algorithm, not this launcher's SHA-256), and copies in
     * fixed-size chunks (rather than one bulk {@code InputStream.transferTo}) so it can report
     * progress after each chunk and check for cancellation - both needed by
     * {@code ModCardView}'s progress ring/cancel button while a quick-install is in flight.
     */
    private static void writeVerifiedSha1(Path target, InputStream source, String expectedSha1Hex,
                                           java.util.function.LongConsumer onProgress,
                                           java.util.function.BooleanSupplier isCancelled) throws IOException {
        Path dir = target.toAbsolutePath().getParent();
        Files.createDirectories(dir);
        Path tmp = Files.createTempFile(dir, target.getFileName().toString(), ".tmp");
        try {
            String actual;
            try (source; var out = Files.newOutputStream(tmp)) {
                DigestOutputStream digestOut = new DigestOutputStream(out, sha1Digest());
                byte[] buffer = new byte[COPY_BUFFER_SIZE];
                long copied = 0;
                int read;
                while ((read = source.read(buffer)) != -1) {
                    if (isCancelled.getAsBoolean()) {
                        throw new InstallCancelledException();
                    }
                    digestOut.write(buffer, 0, read);
                    copied += read;
                    onProgress.accept(copied);
                }
                actual = Hex.encode(digestOut.getMessageDigest().digest());
            }
            if (expectedSha1Hex != null && !actual.equalsIgnoreCase(expectedSha1Hex)) {
                throw new IOException("Hash mismatch for " + target.getFileName()
                        + " (attendu " + expectedSha1Hex + ", obtenu " + actual + ")");
            }
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    /** Thrown by {@link #writeVerifiedSha1} when {@code isCancelled} flips mid-download - caught specially so a cancel doesn't show as a failed install. */
    private static final class InstallCancelledException extends IOException {
    }

    private static MessageDigest sha1Digest() {
        try {
            return MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError("SHA-1 not available", e);
        }
    }
}
