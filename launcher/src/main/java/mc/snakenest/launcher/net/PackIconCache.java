package mc.snakenest.launcher.net;

import com.google.gson.Gson;
import mc.snakenest.launcher.ui.common.RemoteImages;
import mc.snakenest.launcher.util.AtomicFiles;
import mc.snakenest.launcher.util.Sha256;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Objects;
import java.util.function.Function;

/** Durable, site-scoped icons. Offline reads never invoke the downloader or discard old images. */
public final class PackIconCache {
    private static final Gson JSON = new Gson();
    private static final long MAX_BYTES = 1024 * 1024;
    private static final long FRESH_MS = 24 * 60 * 60 * 1000L;
    private final Path directory;
    private final String origin;
    private final Function<String, BufferedImage> downloader;
    private record Metadata(String source, String revision, long fetchedAt) { }

    public PackIconCache(Path directory, String origin) { this(directory, origin, RemoteImages::tryLoad); }
    PackIconCache(Path directory, String origin, Function<String, BufferedImage> downloader) {
        this.directory = directory; this.origin = origin.replaceAll("/+$", ""); this.downloader = downloader;
    }
    private Path file(String identity, String suffix) {
        return directory.resolve(Sha256.hex((origin + "\n" + identity).getBytes(StandardCharsets.UTF_8)) + suffix);
    }
    public BufferedImage cached(String identity) {
        try {
            Path path = file(identity, ".png");
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) > MAX_BYTES) return null;
            try (var input = ImageIO.createImageInputStream(path.toFile())) {
                var readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) return null;
                var reader = readers.next();
                try {
                    reader.setInput(input);
                    if (reader.getWidth(0) > 256 || reader.getHeight(0) > 256) return null;
                    return reader.read(0);
                } finally { reader.dispose(); }
            }
        } catch (Exception ignored) { return null; }
    }
    /** Network allowed only by the caller; a stale or failed refresh retains the last good image. */
    public BufferedImage load(String identity, String source, String revision, boolean allowNetwork) {
        BufferedImage previous = cached(identity);
        if (!allowNetwork || source == null || source.isBlank()) return previous;
        try {
            Path meta = file(identity, ".json");
            if (previous != null && Files.isRegularFile(meta, LinkOption.NOFOLLOW_LINKS) && Files.size(meta) < 16384) {
                Metadata saved = JSON.fromJson(Files.readString(meta), Metadata.class);
                long age = System.currentTimeMillis() - saved.fetchedAt();
                if (Objects.equals(saved.source(), source) && Objects.equals(saved.revision(), revision) && age >= 0 && age < FRESH_MS) return previous;
            }
        } catch (Exception ignored) { }
        try {
            BufferedImage received = downloader.apply(source);
            if (received == null) return previous;
            double scale = Math.min(1, 256.0 / Math.max(received.getWidth(), received.getHeight()));
            BufferedImage icon = new BufferedImage(Math.max(1, (int) (received.getWidth() * scale)), Math.max(1, (int) (received.getHeight() * scale)), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = icon.createGraphics();
            try { g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR); g.drawImage(received, 0, 0, icon.getWidth(), icon.getHeight(), null); }
            finally { g.dispose(); }
            var output = new ByteArrayOutputStream(); ImageIO.write(icon, "png", output);
            if (output.size() <= MAX_BYTES) {
                AtomicFiles.write(file(identity, ".png"), output.toByteArray());
                AtomicFiles.write(file(identity, ".json"), JSON.toJson(new Metadata(source, revision, System.currentTimeMillis())).getBytes(StandardCharsets.UTF_8));
            }
            return icon;
        } catch (Exception ignored) { return previous; }
    }
}
