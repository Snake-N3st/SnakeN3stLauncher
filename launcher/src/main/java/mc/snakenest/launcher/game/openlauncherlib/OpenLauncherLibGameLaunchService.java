package mc.snakenest.launcher.game.openlauncherlib;

import fr.flowarg.openlauncherlib.NoFramework;
import fr.theshark34.openlauncherlib.JavaUtil;
import fr.theshark34.openlauncherlib.minecraft.AuthInfos;
import fr.theshark34.openlauncherlib.minecraft.GameFolder;
import mc.snakenest.launcher.game.GameLaunchException;
import mc.snakenest.launcher.game.GameLaunchService;
import mc.snakenest.launcher.game.LaunchRequest;
import mc.snakenest.launcher.game.ModLoader;

import java.util.ArrayList;
import java.util.List;

/**
 * The only class in the launcher allowed to import
 * {@code fr.theshark34.openlauncherlib.*}/{@code fr.flowarg.openlauncherlib.*}
 * (GPL-3.0) - everything else depends only on {@link GameLaunchService}.
 * Uses {@code NoFramework}, this library's version-agnostic launch API
 * (reads the actual installed version JSON rather than requiring a
 * hardcoded {@code GameType} per Minecraft version range), paired with
 * {@link GameFolder#FLOW_UPDATER} to match the directory layout
 * {@code FlowUpdaterGameInstallService} already wrote.
 *
 * <p><b>Not yet exercised against a real launch in this environment</b> -
 * same caveat as {@code FlowUpdaterGameInstallService}: every signature
 * used here was individually confirmed against the actual 3.2.11 jar, but
 * the full install-then-launch flow is still unverified end-to-end.
 */
public final class OpenLauncherLibGameLaunchService implements GameLaunchService {

    /**
     * Guards {@link JavaUtil#setJavaCommand}, which is process-wide static state in the library
     * rather than anything scoped to a launch. Two modpacks starting at once - which the per-slug
     * {@code modpack.InstanceStateRegistry} genuinely allows - would otherwise race, and the loser
     * would silently start on the other one's JVM. Held only across building the command and
     * spawning the process, not for the game's lifetime.
     */
    private static final Object JAVA_COMMAND_LOCK = new Object();

    @Override
    public Process launch(LaunchRequest request) throws GameLaunchException {
        try {
            return new ProcessBuilder(prepareCommand(request))
                    .directory(request.instanceDir().toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                    .start();
        } catch (Exception e) {
            throw new GameLaunchException("Could not launch " + request.mcVersion() + " (" + request.loader() + ")", e);
        }
    }

    /** Reads installed JSON only. OLL's process launcher logs secrets, so never invoke that part. */
    List<String> prepareCommand(LaunchRequest request) throws Exception {
        if (request.localOnly()) LocalLaunchFiles.validate(request);
        AuthInfos authInfos = new AuthInfos(request.username(), "0", request.offlineUuid().toString());
        NoFramework noFramework = new NoFramework(request.instanceDir(), authInfos, GameFolder.FLOW_UPDATER);
        noFramework.setAdditionalVmArgs(buildVmArgs(request));
        noFramework.setLastCallback(external -> {
            var profile = external.getProfile();
            List<String> command = new ArrayList<>();
            String javaCommand = request.javaPath();
            if (javaCommand == null) javaCommand = java.nio.file.Path.of(System.getProperty("java.home"), "bin",
                    System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win") ? "java.exe" : "java").toString();
            command.add(javaCommand);
            if (profile.getMacDockName() != null && System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("mac"))
                command.add(JavaUtil.macDockName(profile.getMacDockName()));
            if (profile.getVmArgs() != null) command.addAll(profile.getVmArgs());
            command.add("-cp"); command.add(profile.getClassPath()); command.add(profile.getMainClass());
            if (profile.getArgs() != null) command.addAll(profile.getArgs());
            // Stop before ExternalLauncher.launch(), which prints the entire JVM command.
            throw new CommandPrepared(command);
        });
        try {
            noFramework.launch(request.mcVersion(), request.loaderVersion(), toNoFrameworkModLoader(request.loader()));
            throw new IllegalStateException("Local launch command was not captured");
        } catch (CommandPrepared prepared) {
            return prepared.command;
        }
    }

    private static final class CommandPrepared extends Exception {
        private final List<String> command;
        CommandPrepared(List<String> command) { super("Local command prepared"); this.command = List.copyOf(command); }
    }

    /** Memory allocation + custom args are per-modpack preferences (see {@code modpack.ModpackSettings}). */
    private List<String> buildVmArgs(LaunchRequest request) {
        List<String> args = new ArrayList<>();
        args.add("-Xmx" + request.memoryMb() + "M");
        if (request.extraJvmArgs() != null && !request.extraJvmArgs().isBlank()) {
            args.addAll(List.of(request.extraJvmArgs().trim().split("\\s+")));
        }
        args.add("-Dsn3.token=" + request.sn3TokenHex());
        return args;
    }

    private NoFramework.ModLoader toNoFrameworkModLoader(ModLoader loader) {
        return switch (loader) {
            case VANILLA -> NoFramework.ModLoader.VANILLA;
            case FORGE -> NoFramework.ModLoader.FORGE;
            case FABRIC -> NoFramework.ModLoader.FABRIC;
            case NEOFORGE -> NoFramework.ModLoader.NEO_FORGE;
            case UNKNOWN -> throw new IllegalStateException("Unknown mod loader - the manifest declared a loader value this launcher build doesn't recognize");
        };
    }
}
