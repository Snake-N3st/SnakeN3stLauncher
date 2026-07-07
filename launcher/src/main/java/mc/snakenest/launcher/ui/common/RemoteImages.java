package mc.snakenest.launcher.ui.common;

import mc.snakenest.launcher.util.Log;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.time.Duration;

/**
 * Best-effort remote image loading (a client's logo, a modpack's icon) -
 * does real network I/O, so callers must run this off the EDT. Never
 * throws: a missing/unreachable/corrupt image is not worth failing a
 * screen over, callers just get {@code null} and fall back to a
 * placeholder (see {@code ui.LogoPanel}/{@code ui.common.AvatarPanel}).
 */
public final class RemoteImages {

    private RemoteImages() {
    }

    public static BufferedImage tryLoad(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            URL parsed = URI.create(url).toURL();
            var connection = parsed.openConnection();
            connection.setConnectTimeout((int) Duration.ofSeconds(10).toMillis());
            connection.setReadTimeout((int) Duration.ofSeconds(10).toMillis());
            try (InputStream in = connection.getInputStream()) {
                return ImageIO.read(in);
            }
        } catch (Exception e) {
            Log.warn(RemoteImages.class, "Could not load image from " + url + ": " + e.getMessage());
            return null;
        }
    }
}
