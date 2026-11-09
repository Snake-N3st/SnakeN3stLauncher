package mc.snakenest.launcher.benchmark;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BenchmarkResultTest {
    @Test void absentRendererDoesNotClassifyGpu() {
        assertTrue(new BenchmarkResult(150,200,null,null).describe().contains("ne peuvent pas être évalués"));
    }
    @Test void softwareRendererDoesNotRecommendGraphicalUsages() {
        var report=new BenchmarkResult(180,200,"llvmpipe (LLVM)",null).describe();
        assertTrue(report.contains("ne peuvent pas être évalués"));assertFalse(report.contains("Adapté à essayer"));
    }
    @Test void missingCpuDoesNotFabricateUsages() {
        assertTrue(new BenchmarkResult(Double.NaN,200,"GPU",null).describe().contains("Aucun avis fiable"));
    }
    @Test void lowResultsGiveClearConservativeGuidance() {
        var report=new BenchmarkResult(30,2,"GPU",null).describe();
        assertTrue(report.contains("Déconseillé pour cet essai"));assertTrue(report.contains("ne garantit aucun FPS"));
    }
    @Test void hardwareSuccessCanRecommendLightUseWithoutGuaranteeingFps() {
        var report=new BenchmarkResult(150,20,"Hardware GPU",null).describe();
        assertTrue(report.contains("Adapté à essayer : Vanilla"));assertTrue(report.contains("Déconseillé pour cet essai : Modpack lourd"));
    }
}
