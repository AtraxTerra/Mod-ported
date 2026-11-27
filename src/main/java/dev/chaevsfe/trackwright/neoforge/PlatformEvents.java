// Adapted from chaevsfe / Create Fly: Trackwright (https://github.com/chaevsfe/Trackwright)
// Licensed under GPL-3.0-or-later — extracted/adapted with AI assistance.
package dev.chaevsfe.trackwright.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import org.jspecify.annotations.Nullable;

/**
 * Port: the two Fabric API player events upstream calls, on NeoForge's event bus.
 */
public final class PlatformEvents {
    private PlatformEvents() {
    }

    /** Fabric's {@code UseBlockCallback}: anything but PASS stops the click and becomes its result. */
    @FunctionalInterface
    public interface UseBlock {
        InteractionResult interact(Player player, Level level, InteractionHand hand, BlockHitResult hit);
    }

    public static void onUseBlock(UseBlock callback) {
        onUseBlock(EventPriority.NORMAL, callback);
    }

    public static void onUseBlock(EventPriority priority, UseBlock callback) {
        NeoForge.EVENT_BUS.addListener(priority, false, PlayerInteractEvent.RightClickBlock.class, event -> {
            InteractionResult result = callback.interact(event.getEntity(), event.getLevel(), event.getHand(), event.getHitVec());
            if (result != InteractionResult.PASS) {
                event.setCanceled(true);
                event.setCancellationResult(result);
            }
        });
    }

    /**
     * Fabric's {@code PlayerBlockBreakEvents.BEFORE}: whether the other mods (claims, protections) let
     * this player break this block. NeoForge asks them through {@link BreakBlockEvent}.
     */
    public static boolean beforeBlockBreak(Level level, Player player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity) {
        return !NeoForge.EVENT_BUS.post(new BreakBlockEvent(level, pos, state, player)).isCanceled();
    }
}
