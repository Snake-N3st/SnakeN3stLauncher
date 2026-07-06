package mc.snakenest.launcher.util;

import java.util.Locale;

/** Formats a byte count for display (e.g. "104,1 Mo"), French-style units to match the site's own admin UI. */
public final class HumanSize {

    private static final String[] UNITS = {"o", "Ko", "Mo", "Go", "To"};

    private HumanSize() {
    }

    public static String format(long bytes) {
        double value = bytes;
        int unitIndex = 0;
        while (value >= 1024 && unitIndex < UNITS.length - 1) {
            value /= 1024;
            unitIndex++;
        }
        String number = unitIndex == 0
                ? String.valueOf((long) value)
                : String.format(Locale.FRENCH, "%.1f", value);
        return number + " " + UNITS[unitIndex];
    }
}
