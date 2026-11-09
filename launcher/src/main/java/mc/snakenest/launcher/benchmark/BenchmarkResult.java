package mc.snakenest.launcher.benchmark;

import java.util.Locale;

/** Synthetic workloads v1; thresholds are conservative guidance, never Minecraft FPS predictions. */
public record BenchmarkResult(double cpuMillionIterations, double gpuMillionPixels, String renderer, String gpuError) {
    public static boolean isSoftwareRenderer(String renderer) {
        if (renderer == null || renderer.isBlank()) return true;
        String text = renderer.toLowerCase(Locale.ROOT);
        return text.contains("llvmpipe") || text.contains("softpipe") || text.contains("software")
                || text.contains("swiftshader") || text.contains("gdi generic") || text.contains("lavapipe");
    }
    public String describe() {
        StringBuilder text = new StringBuilder("Benchmark court — charge synthétique v1\n\n");
        if (!Double.isFinite(cpuMillionIterations) || cpuMillionIterations <= 0) {
            return text.append("Mesure CPU indisponible. Aucun avis fiable pour cet essai.").toString();
        }
        text.append(String.format(Locale.FRANCE, "CPU : %.1f millions d’itérations/s (un cœur)\n", cpuMillionIterations));
        boolean gpuValid = gpuError == null && Double.isFinite(gpuMillionPixels) && gpuMillionPixels > 0
                && !isSoftwareRenderer(renderer);
        if (gpuValid) text.append(String.format(Locale.FRANCE, "GPU : %.1f millions de pixels/s (shader fixe 512 × 512)\n", gpuMillionPixels));
        else text.append("GPU : mesure matérielle indisponible — ").append(gpuError == null ? "rendu logiciel détecté" : gpuError).append('\n');
        text.append("Rendu : ").append(renderer == null || renderer.isBlank() ? "non identifié" : renderer).append("\n\n");
        if (!gpuValid) {
            text.append("Avis limité au CPU : ").append(cpuMillionIterations >= 120 ? "charge de simulation soutenue envisageable." : cpuMillionIterations >= 40 ? "charges légères à modérées à privilégier." : "charges légères et distance de simulation réduite à privilégier.")
                    .append("\nLes usages graphiques ne peuvent pas être évalués sur cet essai.\n");
        } else {
            text.append("Usages estimés :\n");
            usage(text,"Vanilla / modpack léger, distance réduite",cpuMillionIterations>=40 && gpuMillionPixels>=4);
            usage(text,"Modpack modéré, graphismes simples",cpuMillionIterations>=120 && gpuMillionPixels>=15);
            usage(text,"Modpack lourd",cpuMillionIterations>=240 && gpuMillionPixels>=50);
            usage(text,"Shaders exigeants",cpuMillionIterations>=120 && gpuMillionPixels>=50);
        }
        return text.append("\nIndication approximative : ce test ne mesure pas Minecraft et ne garantit aucun FPS.\nLa RAM, les mods, les pilotes et la chauffe peuvent changer le résultat.\nFerme les tâches lourdes avant de comparer deux essais.").toString();
    }
    private static void usage(StringBuilder text,String usage,boolean suitable) {
        text.append(suitable ? "Adapté à essayer : " : "Déconseillé pour cet essai : ").append(usage).append('\n');
    }
}
