package mc.snakenest.launcher.benchmark;

import java.util.Locale;

/** Synthetic workload measurements only: no calibrated Minecraft performance model exists. */
public record BenchmarkResult(double cpuMillionIterations, double gpuMillionPixels, String renderer, String gpuError) {
    public static boolean isSoftwareRenderer(String renderer) {
        if (renderer == null || renderer.isBlank()) return true;
        String text = renderer.toLowerCase(Locale.ROOT);
        return text.contains("llvmpipe") || text.contains("softpipe") || text.contains("software")
                || text.contains("swiftshader") || text.contains("gdi generic") || text.contains("lavapipe");
    }

    public String describe() {
        StringBuilder text = new StringBuilder("Résultat du benchmark court\n");
        text.append("Mesures synthétiques uniquement : les performances de Minecraft restent à vérifier en jeu.\n\n");
        text.append("MESURES\n");
        if (Double.isFinite(cpuMillionIterations) && cpuMillionIterations > 0) {
            text.append(String.format(Locale.FRANCE, "CPU : %.1f millions d’itérations/s (un cœur)\n", cpuMillionIterations));
        } else {
            text.append("CPU : mesure indisponible ou invalide\n");
        }
        boolean gpuValid = gpuError == null && Double.isFinite(gpuMillionPixels) && gpuMillionPixels > 0
                && !isSoftwareRenderer(renderer);
        if (gpuValid) {
            text.append(String.format(Locale.FRANCE, "GPU : %.1f millions de pixels/s\n", gpuMillionPixels));
            double milliseconds = 512d * 512d / (gpuMillionPixels * 1_000_000d) * 1000d;
            if (Double.isFinite(milliseconds) && milliseconds > 0) {
                text.append(String.format(Locale.FRANCE, "Durée moyenne d’une image du test GPU : %.3f ms\n", milliseconds));
            }
        } else {
            String reason = gpuError != null ? gpuError
                    : renderer == null || renderer.isBlank() ? "rendu non identifié"
                    : isSoftwareRenderer(renderer) ? "rendu logiciel détecté" : "mesure invalide";
            text.append("GPU : mesure matérielle indisponible — ").append(reason).append('\n');
        }
        text.append("Rendu : ").append(renderer == null || renderer.isBlank() ? "non identifié" : renderer);
        text.append("\n\nPROTOCOLE v1\n");
        text.append("CPU : mélange d’entiers 32 bits, 0,5 s de chauffe puis 3 s de mesure.\n");
        text.append("GPU : image fixe 512 × 512, shader de 48 étapes, synchronisation après chaque image ; 0,5 s de chauffe puis 4 s de mesure.\n");
        text.append("La durée GPU concerne uniquement cette petite image synthétique, pas une image Minecraft.\n\n");
        text.append("USAGES\n");
        text.append("Minecraft léger / vanilla : à vérifier en jeu.\n");
        text.append("Modpacks lourds : non validés, essai réel nécessaire.\n");
        text.append("Shaders : non recommandés sans validation en jeu.\n");
        text.append("Objectif 120 Hz (8,33 ms par image) : non évalué par ce test. Les temps CPU/GPU mesurés ne sont pas des temps d’image Minecraft.\n\n");
        text.append("Ce test ne mesure pas Minecraft, ne prédit aucun FPS et ne valide aucun modpack.\n");
        text.append("La RAM, les mods, les pilotes, le moteur de rendu et la chauffe influencent les performances réelles.\n");
        text.append("Pour comparer deux essais, utilise le même protocole et ferme les tâches lourdes.");
        return text.toString();
    }
}
