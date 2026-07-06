package mc.snakenest.launcher.game;

/** Spawns the actual Minecraft process for an already-installed instance. */
public interface GameLaunchService {

    /** @return the spawned Minecraft process; the caller decides whether/how to watch it */
    Process launch(LaunchRequest request) throws GameLaunchException;
}
