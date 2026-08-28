package org.lts.callout;

public final class WorldScopeTracker {
    // Written from the network thread (login/respawn packet handlers inject at HEAD,
    // before Minecraft reschedules onto the client thread) and read from the client
    // thread when computing the history scope.
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
