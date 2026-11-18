package com.zurrtum.create.client.flywheel.impl.compat;

/**
 * Iris integration, inert on this loader.
 *
 * <p>Upstream calls {@code IrisApi} to ask whether a shader pack is active, so that Flywheel can
 * skip its own instanced rendering while a pack is driving the pipeline. Iris is a Fabric mod and
 * its API is not on this classpath, so this reports "no shader pack" and Flywheel takes its normal
 * path.
 *
 * <p>The class is kept rather than excluded because {@code FlwLibLinkImpl} and
 * {@code BlockEntityTypeMixin} both reference it; stubbing it keeps those compiling. Upstream's
 * shape made this easy -- every real API call was already behind an {@code ACTIVE} guard in a
 * nested {@code Internals} class, so only that class had to go.
 *
 * <p>TODO(port): to support a shader mod on NeoForge, restore an {@code Internals} that talks to
 * whichever one is being targeted and make {@code ACTIVE} detect it.
 */
public final class IrisCompat {
    public static final boolean ACTIVE = false;

    private IrisCompat() {
    }

    public static boolean isShaderPackInUse() {
        return false;
    }

    public static boolean isRenderingShadowPass() {
        return false;
    }
}
