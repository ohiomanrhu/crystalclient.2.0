package dev.crystalclient.core;

import dev.crystalclient.CrystalClient;

/**
 * Central arbiter for inventory/hand actions. One owner at a time; a lower level number preempts a higher one,
 * equal levels never preempt each other. Locks expire automatically so a crashed module cannot starve the rest.
 */
public final class PriorityManager {
    private static Module owner;
    private static int ownerLevel = 99;
    private static long expires;
    private static long tick;
    public static int conflicts;

    private PriorityManager() {}

    public static long now() { return tick; }

    public static void tick() {
        tick++;
        if (owner != null && tick >= expires) release();
    }

    public static boolean acquire(Module m, int level, int holdTicks) {
        if (owner == null || owner == m || level < ownerLevel) {
            if (owner != null && owner != m)
                CrystalClient.debug("{} (tier {}) preempted {} (tier {})", m.name, level, owner.name, ownerLevel);
            owner = m; ownerLevel = level; expires = tick + Math.max(1, holdTicks);
            return true;
        }
        conflicts++;
        CrystalClient.debug("CONFLICT: {} (tier {}) denied, held by {} (tier {})", m.name, level, owner.name, ownerLevel);
        return false;
    }

    public static boolean isFree() { return owner == null; }
    public static Module owner() { return owner; }
    public static void release() { owner = null; ownerLevel = 99; }
    public static void reset() { release(); }
}
