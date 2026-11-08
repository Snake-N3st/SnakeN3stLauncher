package mc.snakenest.launcher.ui.modpack;

import mc.snakenest.launcher.modpack.InstalledModInfo;
import mc.snakenest.launcher.modpack.ModMeta;
import mc.snakenest.launcher.modpack.ModMetadataReader;
import mc.snakenest.launcher.modpack.ModpackManifest;
import mc.snakenest.launcher.modpack.ModsMetaStore;
import mc.snakenest.launcher.ui.common.AvatarPanel;
import mc.snakenest.launcher.ui.common.Borders;
import mc.snakenest.launcher.ui.common.Buttons;
import mc.snakenest.launcher.ui.common.Icons;
import mc.snakenest.launcher.ui.common.VerticalListPanel;
import mc.snakenest.launcher.util.Log;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.GridBagLayout;
import java.awt.event.InputEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * "Gérer les mods" - a page pushed into {@code ModpackSectionPage} (its own "Retour" goes back to
 * the modpack's detail page), not a popup: the list of mods actually present in {@code mods/}
 * right now, read from each jar's own bundled metadata via {@link ModMetadataReader} rather than
 * this launcher's own {@code mods-meta.json}/Modrinth tracking - that way a manually-dropped-in
 * {@code .jar} (or one Modrinth has never heard of) still shows a real name/version/author instead
 * of just its filename.
 *
 * <p>Only the "Ajouter" button opens a real popup ({@link ModManagerDialog}, the search/browse/
 * install flow) - this page itself stays part of the main window. Each row's remove button shows
 * a confirmation warning unless Shift is held at click time, the same "hold Shift to skip the
 * confirmation" shortcut Discord uses for deleting a message.
 */
public final class InstalledModsPage extends JPanel {

    private static final int HEADER_ICON_SIZE = 22;
    private static final int ROW_ACTION_ICON_SIZE = 20;
    private static final int ROW_ACTION_BUTTON_SIZE = 40;

    private final ModpackManifest manifest;
    private final Path instanceDir;
    private final Path modsDir;
    private final ExecutorService executor;
    private final ModsMetaStore metaStore = new ModsMetaStore();
    private final VerticalListPanel listPanel = new VerticalListPanel();
    private final JLabel statusLabel = new JLabel(" ");

    public InstalledModsPage(ModpackManifest manifest, Path instanceDir, ExecutorService executor, Runnable onBack) {
        this.manifest = manifest;
        this.instanceDir = instanceDir;
        this.modsDir = instanceDir.resolve("mods");
        this.executor = executor;

        setLayout(new BorderLayout(0, 16));
        setBorder(new EmptyBorder(24, 24, 24, 24));

        JPanel headerRow = new JPanel(new BorderLayout(8, 0));
        headerRow.add(Buttons.flatIcon(Icons.backArrow(20), "Retour", onBack), BorderLayout.WEST);
        JLabel title = new JLabel("Mods installés");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        headerRow.add(title, BorderLayout.CENTER);

        JPanel headerActions = new JPanel();
        headerActions.setOpaque(false);
        headerActions.add(Buttons.iconButton(Icons.folder(HEADER_ICON_SIZE), "Ouvrir le dossier des mods", this::openModsFolder));
        headerActions.add(Buttons.iconButton(Icons.download(HEADER_ICON_SIZE), "Ajouter un mod", this::openAddDialog));
        headerRow.add(headerActions, BorderLayout.EAST);
        add(headerRow, BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane(listPanel);
        scroll.setBorder(Borders.none());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);

        statusLabel.setBorder(new EmptyBorder(8, 0, 0, 0));
        add(statusLabel, BorderLayout.SOUTH);

        executor.submit(this::refresh);
    }

    /** {@code ModManagerDialog} is modal, so control only returns here once it's closed - refresh right after to pick up whatever changed. */
    private void openAddDialog() {
        ModManagerDialog.show(this, manifest, instanceDir, executor);
        executor.submit(this::refresh);
    }

    private void openModsFolder() {
        try {
            Files.createDirectories(modsDir);
            java.awt.Desktop.getDesktop().open(modsDir.toFile());
        } catch (IOException e) {
            Log.warn(InstalledModsPage.class, "Could not open " + modsDir + ": " + e.getMessage());
        }
    }

    private void refresh() {
        List<Path> files;
        if (!Files.isDirectory(modsDir)) {
            files = List.of();
        } else {
            try (var stream = Files.list(modsDir)) {
                files = stream.filter(p -> p.getFileName().toString().endsWith(".jar"))
                        .sorted(Comparator.comparing(p -> p.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                        .toList();
            } catch (IOException e) {
                Log.warn(InstalledModsPage.class, "Could not list " + modsDir + ": " + e.getMessage());
                files = List.of();
            }
        }
        List<Row> rows = new ArrayList<>();
        for (Path file : files) {
            rows.add(new Row(file, ModMetadataReader.read(file)));
        }
        List<Row> finalRows = rows;
        SwingUtilities.invokeLater(() -> renderRows(finalRows));
    }

    private void renderRows(List<Row> rows) {
        listPanel.removeAll();
        if (rows.isEmpty()) {
            JLabel empty = new JLabel("Aucun mod installé.");
            empty.setForeground(UIManager.getColor("Label.disabledForeground"));
            listPanel.add(empty);
        }
        for (Row row : rows) {
            JPanel card = buildRow(row);
            card.setAlignmentX(Component.LEFT_ALIGNMENT);
            listPanel.add(card);
            listPanel.add(Box.createVerticalStrut(10));
        }
        listPanel.revalidate();
        listPanel.repaint();
        statusLabel.setText(rows.size() + " mod(s) installé(s).");
    }

    private JPanel buildRow(Row row) {
        JPanel card = new JPanel(new BorderLayout(16, 0));
        card.setOpaque(false);
        card.setBorder(Borders.card());

        InstalledModInfo info = row.info();
        Color mutedColor = UIManager.getColor("Label.disabledForeground");

        AvatarPanel avatar = new AvatarPanel(info.name(), 48);
        JPanel avatarWrapper = new JPanel(new BorderLayout());
        avatarWrapper.setOpaque(false);
        avatarWrapper.setBorder(new EmptyBorder(0, 10, 0, 0));
        avatarWrapper.add(avatar, BorderLayout.CENTER);
        card.add(avatarWrapper, BorderLayout.WEST);
        if (info.iconPath() != null) {
            executor.submit(() -> {
                var icon = ModMetadataReader.readIcon(row.file(), info.iconPath());
                if (icon != null) {
                    SwingUtilities.invokeLater(() -> avatar.setImage(icon));
                }
            });
        }

        JPanel textPanel = new JPanel();
        textPanel.setOpaque(false);
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
        textPanel.setBorder(new EmptyBorder(10, 14, 10, 0));

        JLabel nameLabel = new JLabel(info.name());
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 15f));

        StringBuilder meta = new StringBuilder();
        if (info.version() != null) {
            meta.append(info.version());
        }
        if (info.author() != null) {
            if (meta.length() > 0) {
                meta.append("  —  ");
            }
            meta.append("par ").append(info.author());
        }
        JLabel metaLabel = new JLabel(meta.length() > 0 ? meta.toString() : row.file().getFileName().toString());
        metaLabel.setForeground(mutedColor);

        nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        metaLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        textPanel.add(nameLabel);
        textPanel.add(metaLabel);
        card.add(textPanel, BorderLayout.CENTER);

        // Same hover-only circular style as ui.modpack.ModpackCardView/ModCardView's own
        // per-row quick action - a resting background here would repeat once per row.
        JButton removeButton = Buttons.roundActionButton(ROW_ACTION_BUTTON_SIZE);
        removeButton.setIcon(Icons.trash(ROW_ACTION_ICON_SIZE));
        removeButton.setToolTipText("Retirer (Maj+clic pour retirer immédiatement, sans confirmation)");
        removeButton.setForeground(mc.snakenest.launcher.ui.common.Colors.danger());
        removeButton.addActionListener(e -> {
            boolean skipConfirm = (e.getModifiers() & InputEvent.SHIFT_MASK) != 0;
            removeMod(row.file(), skipConfirm);
        });

        JPanel buttonWrapper = new JPanel(new GridBagLayout());
        buttonWrapper.setOpaque(false);
        buttonWrapper.setBorder(new EmptyBorder(0, 0, 0, 12));
        buttonWrapper.add(removeButton);
        card.add(buttonWrapper, BorderLayout.EAST);

        return card;
    }

    private void removeMod(Path file, boolean skipConfirm) {
        if (!skipConfirm) {
            int choice = JOptionPane.showConfirmDialog(this,
                    "Supprimer définitivement \"" + file.getFileName() + "\" ?",
                    "Retirer le mod", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (choice != JOptionPane.YES_OPTION) {
                return;
            }
        }
        executor.submit(() -> {
            try {
                Files.deleteIfExists(file);
                // Keep ModManagerDialog's own "installé"/"retirer" tracking in sync - it reads
                // the same mods-meta.json, and would otherwise still think this file exists.
                Map<String, ModMeta> meta = metaStore.load(instanceDir);
                if (meta.remove(file.getFileName().toString()) != null) {
                    metaStore.save(instanceDir, meta);
                }
            } catch (IOException e) {
                Log.warn(InstalledModsPage.class, "Could not remove " + file + ": " + e.getMessage());
            }
            refresh();
        });
    }

    private record Row(Path file, InstalledModInfo info) {
    }
}
