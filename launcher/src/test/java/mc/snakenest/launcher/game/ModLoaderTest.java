package mc.snakenest.launcher.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModLoaderTest {

    @Test
    void mapsEachKnownApiValue() {
        assertEquals(ModLoader.VANILLA, ModLoader.fromApiValue("vanilla"));
        assertEquals(ModLoader.FORGE, ModLoader.fromApiValue("forge"));
        assertEquals(ModLoader.FABRIC, ModLoader.fromApiValue("fabric"));
        assertEquals(ModLoader.NEOFORGE, ModLoader.fromApiValue("neoforge"));
    }

    @Test
    void isCaseInsensitive() {
        assertEquals(ModLoader.FORGE, ModLoader.fromApiValue("Forge"));
    }

    @Test
    void fallsBackToUnknownForAnythingElse() {
        assertEquals(ModLoader.UNKNOWN, ModLoader.fromApiValue("quilt"));
        assertEquals(ModLoader.UNKNOWN, ModLoader.fromApiValue(null));
    }
}
