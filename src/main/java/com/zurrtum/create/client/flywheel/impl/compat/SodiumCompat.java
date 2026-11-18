package com.zurrtum.create.client.flywheel.impl.compat;

import com.zurrtum.create.client.flywheel.api.visualization.BlockEntityVisualizer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.jspecify.annotations.Nullable;

/**
 * Sodium integration, inert on this loader.
 *
 * <p>Upstream registers a Sodium render predicate per block-entity type, so that a block entity
 * Flywheel has taken over is not also drawn by Sodium's own block-entity renderer. Sodium is a
 * Fabric mod and its API is not on this classpath, so nothing is registered and the vanilla
 * renderer path applies.
 *
 * <p>Kept rather than excluded because {@code FlwLibLinkImpl} and {@code BlockEntityTypeMixin}
 * reference it. The signature is unchanged, including the opaque {@code Object} predicate handle,
 * so callers are untouched; it simply always returns {@code null}.
 *
 * <p>TODO(port): double rendering -- a block entity drawn twice, by Flywheel and by the vanilla
 * renderer -- is what this integration exists to prevent. On NeoForge that has to be solved
 * against whatever renderer is actually in play, so if Create's contraptions or kinetics look
 * doubled or ghosted, this class is where to start.
 */
public final class SodiumCompat {
    public static final boolean ACTIVE = false;

    private SodiumCompat() {
    }

    @Nullable
    public static <T extends BlockEntity> Object onSetBlockEntityVisualizer(
        BlockEntityType<T> type,
        @Nullable BlockEntityVisualizer<? super T> oldVisualizer,
        @Nullable BlockEntityVisualizer<? super T> newVisualizer,
        @Nullable Object predicate
    ) {
        return null;
    }
}
