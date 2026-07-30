package mc.snakenest.launcher.bootstrap;

import java.util.regex.Pattern;

/**
 * Validates a resolved {@code sn3.clientId} value against the shape the site actually issues
 * ({@code LauncherClient::boot()}, Laravel's {@code Str::random(32)}: exactly 32 alphanumeric
 * characters), with some slack on length in case that constant ever changes server-side - a
 * sanity check against an empty/whitespace/corrupted value, not strict format enforcement.
 *
 * <p>Resolving the value itself - a {@code -D} argument, a {@code bootstrap.properties} file next
 * to the jar, or a {@code jvm.prop} classpath resource bundled inside this specific build of the
 * jar - is {@code BootstrapMain}'s job (see {@link BundledJvmProperties} and {@code
 * loadPropertiesFileNextToJar}), all applied as plain {@code System} properties before this class
 * is ever consulted. This class used to also read a dedicated {@code .clientId} classpath resource
 * itself; that's folded into the more general {@code jvm.prop} mechanism instead, which can also
 * carry {@code sn3.baseUrl}.
 */
final class ClientIds {

    private static final Pattern VALID_SHAPE = Pattern.compile("[A-Za-z0-9]{16,64}");

    private ClientIds() {
    }

    /** @return {@code candidate} if it looks like a real client id, {@code null} otherwise */
    static String resolve(String candidate) {
        return isValid(candidate) ? candidate : null;
    }

    private static boolean isValid(String candidate) {
        return candidate != null && VALID_SHAPE.matcher(candidate).matches();
    }
}
