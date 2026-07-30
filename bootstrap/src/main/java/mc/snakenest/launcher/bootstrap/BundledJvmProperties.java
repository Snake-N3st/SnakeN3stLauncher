package mc.snakenest.launcher.bootstrap;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Loads {@value #RESOURCE_PATH}, a Java properties file bundled as a classpath resource inside
 * this specific build of the bootstrap jar - typically {@code sn3.clientId}/{@code sn3.baseUrl},
 * baked in by the site (see {@code SnakeN3stLogin}'s {@code BootstrapJvmPropInjector}) when it
 * publishes a bootstrap release or builds the portable/installer distributions. Lets a player's
 * downloaded jar work with zero configuration - no {@code -D} arguments, no external file next to
 * it - while a generic, unbuilt checkout of this repo (or a debug run) simply has no such resource
 * and falls through to whatever {@code -D}/{@code bootstrap.properties} already provide.
 *
 * <p>Applied with the <b>lowest</b> precedence of every property source this bootstrap reads
 * ({@code -D} beats {@code bootstrap.properties} next to the jar beats this bundled resource):
 * only fills in a system property that isn't already set, the same "most specific override wins"
 * rule {@code BootstrapMain#loadPropertiesFileNextToJar} already applies to its own file. That
 * means {@link #loadIfPresent()} must run <b>after</b> {@code loadPropertiesFileNextToJar}, not
 * before - otherwise a value baked in here would already be set by the time the external file's
 * own "only if not already set" check runs, and it could never override it.
 */
final class BundledJvmProperties {

    private static final String RESOURCE_PATH = "/jvm.prop";

    private BundledJvmProperties() {
    }

    /**
     * Best-effort, same reasoning as {@code loadPropertiesFileNextToJar}: runs before {@code
     * AppDirs}/{@code Log} exist, so a failure here can only be reported to stderr, and is never
     * fatal - falls back to whatever {@code -D}/{@code bootstrap.properties} already apply, exactly
     * as if the resource didn't exist.
     */
    static void loadIfPresent() {
        try (InputStream in = BundledJvmProperties.class.getResourceAsStream(RESOURCE_PATH)) {
            if (in == null) {
                return;
            }
            Properties properties = new Properties();
            properties.load(in);
            for (String key : properties.stringPropertyNames()) {
                if (System.getProperty(key) == null) {
                    System.setProperty(key, properties.getProperty(key));
                }
            }
        } catch (IOException e) {
            System.err.println("Could not read bundled " + RESOURCE_PATH + ": " + e.getMessage());
        }
    }
}
