package org.lts.callout;

public final class WorldScopeTracker {

    private static volatile boolean seedKnown;
    private static volatile long seed;

    private WorldScopeTracker() {
    }

    public static void setSeed(long value) {
        seed = value;
        seedKnown = true;
    }

    public static void clear() {
        seedKnown = false;
        seed = 0L;
    }

    public static String seedSuffix() {
        return seedKnown ? "|seed:" + Long.toUnsignedString(seed, 16) : "";
    }
}
