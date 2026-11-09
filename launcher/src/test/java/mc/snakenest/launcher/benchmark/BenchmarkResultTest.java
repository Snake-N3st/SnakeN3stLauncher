package mc.snakenest.launcher.benchmark;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BenchmarkResultTest {
    @Test void s21SyntheticScoresNeverValidateHeavyModpacksOrShaders() {
        String report = new BenchmarkResult(552, 121, "Adreno 660", null).describe();
        assertTrue(report.contains("CPU : 552,0"));
        assertTrue(report.contains("GPU : 121,0"));
        assertTrue(report.contains("2,166 ms"));
        assertTrue(report.contains("Modpacks lourds : non validés"));
        assertTrue(report.contains("Shaders : non recommandés sans validation en jeu"));
        assertTrue(report.contains("120 Hz (8,33 ms par image) : non évalué"));
        assertFalse(report.contains("Adapté à essayer"));
        assertFalse(report.contains("Usages estimés"));
    }
    @Test void softwareAndMissingRenderersCannotValidateGpu() {
        for (String renderer : new String[]{null, "", "llvmpipe (LLVM)", "SwiftShader"}) {
            String report = new BenchmarkResult(180, 200, renderer, null).describe();
            assertTrue(report.contains("GPU : mesure matérielle indisponible"));
            assertFalse(report.contains("Durée moyenne d’une image"));
            assertTrue(report.contains("CPU : 180,0"));
        }
    }
    @Test void invalidCpuDoesNotHideValidGpu() {
        for (double cpu : new double[]{Double.NaN, Double.POSITIVE_INFINITY, 0, -1}) {
            String report = new BenchmarkResult(cpu, 121, "Hardware GPU", null).describe();
            assertTrue(report.contains("CPU : mesure indisponible ou invalide"));
            assertTrue(report.contains("GPU : 121,0"));
            assertFalse(report.contains("NaN"));
            assertFalse(report.contains("Infinity"));
        }
    }
    @Test void invalidGpuDoesNotHideValidCpuOrShowTiming() {
        for (double gpu : new double[]{Double.NaN, Double.NEGATIVE_INFINITY, 0, -1}) {
            String report = new BenchmarkResult(552, gpu, "Hardware GPU", null).describe();
            assertTrue(report.contains("CPU : 552,0"));
            assertTrue(report.contains("GPU : mesure matérielle indisponible"));
            assertFalse(report.contains("Durée moyenne d’une image"));
        }
    }
    @Test void protocolAndLimitsRemainExplicitEvenForVeryHighScores() {
        String report = new BenchmarkResult(10000, 10000, "Hardware GPU", null).describe();
        assertTrue(report.contains("512 × 512"));
        assertTrue(report.contains("48 étapes"));
        assertTrue(report.contains("3 s de mesure"));
        assertTrue(report.contains("4 s de mesure"));
        assertTrue(report.contains("ne prédit aucun FPS"));
        assertTrue(report.contains("Modpacks lourds : non validés"));
    }
}
