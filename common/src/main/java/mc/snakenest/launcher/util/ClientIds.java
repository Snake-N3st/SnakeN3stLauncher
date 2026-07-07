package mc.snakenest.launcher.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * Resolves {@code sn3.clientId}: the JVM system property if set, otherwise a
 * {@code .clientId} file bundled as a classpath resource - lets an operator
 * ship a turnkey single-client build of {@code bootstrap}/{@code launcher}
 * that needs no {@code -Dsn3.clientId=...} argument at all (a plain
 * double-clickable jar/shortcut), while still supporting the JVM property
 * for local development or a multi-client build.
 *
 * <p>The resource file's entire content (trimmed) is taken as the client id
 * - no key=value wrapping, just the id itself, since that's all it needs to
 * hold. Validated against the shape the site actually issues
 * ({@code LauncherClient::boot()}, Laravel's {@code Str::random(32)}: exactly
 * 32 alphanumeric characters) with some slack on length in case that
 * constant ever changes server-side - this is a sanity check against an
 * empty/whitespace/corrupted file, not strict format enforcement.
 */
public final class ClientIds {

    private static final String RESOURCE_PATH = "/.clientId";
    private static final Pattern VALID_SHAPE = Pattern.compile("[A-Za-z0-9]{16,64}");

    private ClientIds() {
    }

    /**
     * @param systemProperty the value of {@code System.getProperty("sn3.clientId")} at the call
     *                       site (passed in rather than read here so callers keep control of
     *                       exactly which property name they read)
     * @return the resolved client id, or {@code null} if neither source has a valid one
     */
    public static String resolve(String systemProperty) {
        if (isValid(systemProperty)) {
            return systemProperty;
        }
        String fromResource = readBundledResource();
        return isValid(fromResource) ? fromResource : null;
    }

    private static String readBundledResource() {
        try (InputStream in = ClientIds.class.getResourceAsStream(RESOURCE_PATH)) {
            if (in == null) {
                return null;
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            return null;
        }
    }

    private static boolean isValid(String candidate) {
        return candidate != null && VALID_SHAPE.matcher(candidate).matches();
    }
}
