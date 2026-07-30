package mc.snakenest.launcher.config;

/**
 * Where a modpack's vanilla Minecraft assets/libraries live - the "Stockage des ressources
 * vanilla" setting. Mod files themselves are never affected by this; only the vanilla
 * objects/libraries FlowUpdater installs alongside them (see {@code game.assets.SharedAssetsLinker}
 * in the sibling package for how each mode is actually implemented).
 */
public enum AssetStorageMode {
    /** Shared across every modpack, in the launcher's own data directory. Default. */
    LAUNCHER_SHARED,

    /** Shared with the official Minecraft launcher's (or TLauncher's default) {@code .minecraft} folder. */
    OFFICIAL_MINECRAFT,

    /** Each modpack keeps its own independent copy - today's behavior, no sharing at all. */
    PER_MODPACK
}
