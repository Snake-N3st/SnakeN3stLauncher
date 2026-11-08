package mc.snakenest.launcher.bootstrap;

/**
 * Orders dotted numeric versions ({@code 2.4.0}) - only enough for "is this older than the newest one I have
 * verified". A version this cannot read compares as equal to everything rather than guessing, so that an
 * unusual tag can never produce a spurious "rollback" warning.
 */
final class VersionCompare {

    private VersionCompare() {
    }

    /** Negative when {@code a} is older than {@code b}, positive when newer, zero when equal or unreadable. */
    static int compare(String a, String b) {
        long[] left = parse(a);
        long[] right = parse(b);

        if (left == null || right == null) {
            return 0;
        }

        for (int i = 0; i < Math.max(left.length, right.length); i++) {
            long l = i < left.length ? left[i] : 0;
            long r = i < right.length ? right[i] : 0;

            if (l != r) {
                return Long.compare(l, r);
            }
        }

        return 0;
    }

    private static long[] parse(String version) {
        if (version == null || !version.matches("\\d{1,9}(\\.\\d{1,9})*")) {
            return null;
        }

        String[] parts = version.split("\\.");
        long[] numbers = new long[parts.length];

        for (int i = 0; i < parts.length; i++) {
            numbers[i] = Long.parseLong(parts[i]);
        }

        return numbers;
    }
}
