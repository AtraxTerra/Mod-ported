package com.zurrtum.create.platform;

/**
 * Fluid volume conversion between Create's internal unit and NeoForge's.
 *
 * <h2>Why this class exists</h2>
 * Create Fly's own {@code FluidStack} counts <b>droplets</b> -- the Fabric Transfer API unit, where
 * one bucket is 81000. The constant 81000 appears 20 times in the upstream source. NeoForge's
 * {@code FluidStack} counts <b>millibuckets</b>, where one bucket is 1000.
 * <p>
 * Every value crossing the boundary between Create's inventories and a NeoForge
 * {@code IFluidHandler} must be converted. Getting this wrong does not throw: it silently
 * multiplies or divides every fluid amount in the game by 81, which reads as "fluid duplication" or
 * "tanks drain instantly" and is painful to trace back. So the conversion lives in exactly one
 * place, and the adapters call nothing else.
 *
 * <h2>The rounding rule</h2>
 * 81000 / 81 = 1000, so millibuckets convert to droplets exactly. The reverse truncates: 80 droplets
 * is 0 mB. That truncation must always lose fluid rather than create it, which means:
 * <ul>
 *   <li>When reporting what Create holds to an outside handler, round <b>down</b> ({@link #toMillibuckets}).</li>
 *   <li>When accepting an amount from outside, convert mB to droplets exactly ({@link #toDroplets}).</li>
 *   <li>When Create <b>gives</b> fluid away, convert the mB the other side accepted back to droplets
 *       and remove exactly that -- never remove the droplet amount you offered.</li>
 * </ul>
 * The last rule is the one that leaks fluid if ignored: offering 1040 droplets reports 12 mB, the
 * receiver takes 12 mB, and Create must remove 972 droplets, not 1040.
 */
public final class FluidUnits {
    /** Droplets in one bucket, as used throughout Create's own fluid code. */
    public static final int DROPLETS_PER_BUCKET = 81_000;

    /** Millibuckets in one bucket, as used by NeoForge. */
    public static final int MILLIBUCKETS_PER_BUCKET = 1_000;

    /** Droplets in one millibucket. Exact: 81000 / 1000. */
    public static final int DROPLETS_PER_MILLIBUCKET = DROPLETS_PER_BUCKET / MILLIBUCKETS_PER_BUCKET;

    private FluidUnits() {
    }

    /**
     * Create droplets to NeoForge millibuckets, rounding <b>down</b> so the conversion never invents
     * fluid. Saturates instead of overflowing.
     */
    public static int toMillibuckets(long droplets) {
        long mb = droplets / DROPLETS_PER_MILLIBUCKET;
        return (int) Math.min(mb, Integer.MAX_VALUE);
    }

    /** NeoForge millibuckets to Create droplets. Exact. Saturates instead of overflowing. */
    public static int toDroplets(int millibuckets) {
        long droplets = (long) millibuckets * DROPLETS_PER_MILLIBUCKET;
        return (int) Math.min(droplets, Integer.MAX_VALUE);
    }

    /**
     * The droplet remainder that cannot be expressed in whole millibuckets. Useful when an adapter
     * needs to know it is hiding a sub-millibucket amount from the outside world.
     */
    public static int dropletRemainder(long droplets) {
        return (int) (droplets % DROPLETS_PER_MILLIBUCKET);
    }
}
