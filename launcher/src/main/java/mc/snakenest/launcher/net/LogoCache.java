package mc.snakenest.launcher.net;

import mc.snakenest.launcher.util.AppDirs;
import mc.snakenest.launcher.util.AtomicFiles;
import mc.snakenest.launcher.util.Log;
import mc.snakenest.launcher.util.Sha256;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * On-disk copy of the client's logo, so a start does not have to wait on the network to put a
 * branded window on screen.
 *
 * <p><b>Why.</b> The logo used to be downloaded on every single start, inside the startup budget
 * and before any window existed - so every player paid a network round-trip, on every launch, for
 * an image that changes maybe once a year. Now the cached copy paints immediately and the network
 * check happens afterwards, once the launcher window is already up, where a slow or unreachable
 * server costs nobody anything.
 *
 * <p>Freshness is settled by hashing rather than by any cache header: the site serves this image
 * from a route with no validators worth trusting, and comparing SHA-256 is both exact and cheap on
 * a file this size. A changed logo is written back and handed to the caller to apply live; an
 * unchanged one costs a download and nothing else.
 *
 * <p>Every method is best-effort and returns {@code null} rather than throwing - a missing or
 * corrupt logo is a placeholder, never a reason to fail a screen.
 */
public final class LogoCache {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    /** PNG regardless of what the server sent: ImageIO re-encodes on write, so the extension is ours to pick. */
    private static final String FILE_NAME = "client-logo.png";

    private final Path file;

    public LogoCache(AppDirs dirs) {
        this.file = dirs.cache().resolve(FILE_NAME);
    }

    /**
     * The last logo seen, decoded from disk. No network, safe to call on the EDT, and {@code null}
     * before the first successful fetch ever completes.
     */
    public BufferedImage cached() {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            return ImageIO.read(file.toFile());
        } catch (Exception e) {
            Log.warn(LogoCache.class, "Could not read the cached logo: " + e.getMessage());
            return null;
        }
    }

    /**
     * Downloads {@code url} and compares it with what is already cached.
     *
     * <p>Does real network I/O - callers must run it off the EDT.
     *
     * @return the new image when the logo actually changed (so the caller can swap it in live), or
     *         {@code null} when it is unchanged, unreachable, or unreadable - all three of which
     *         mean "keep showing what is already on screen"
     */
    public BufferedImage refreshIfChanged(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }

        try {
            byte[] downloaded = download(url);

            if (downloaded.length == 0) {
                return null;
            }

            if (Sha256.hex(downloaded).equalsIgnoreCase(cachedSha256())) {
                return null;
            }

            BufferedImage image = ImageIO.read(new ByteArrayInputStream(downloaded));

            if (image == null) {
                Log.warn(LogoCache.class, "The logo at " + url + " is not a readable image; keeping the cached one.");
                return null;
            }

            // Written only after it has been decoded successfully, so a truncated or corrupt
            // download can never replace a good cached copy.
            AtomicFiles.write(file, downloaded);
            Log.info(LogoCache.class, "Client logo updated from " + url);

            return image;
        } catch (Exception e) {
            Log.warn(LogoCache.class, "Could not refresh the client logo: " + e.getMessage());
            return null;
        }
    }

    /** Empty string when nothing is cached, which never equals a real digest. */
    private String cachedSha256() {
        if (!Files.isRegularFile(file)) {
            return "";
        }
        try (InputStream in = Files.newInputStream(file)) {
            return Sha256.hex(in);
        } catch (Exception e) {
            return "";
        }
    }

    private static byte[] download(String url) throws Exception {
        URL parsed = URI.create(url).toURL();
        var connection = parsed.openConnection();
        connection.setConnectTimeout((int) TIMEOUT.toMillis());
        connection.setReadTimeout((int) TIMEOUT.toMillis());

        try (InputStream in = connection.getInputStream()) {
            return in.readAllBytes();
        }
    }
}
