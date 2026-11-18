package com.zurrtum.create.platform;

import com.zurrtum.create.content.equipment.armor.DivingHelmetItem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.neoforged.neoforge.event.entity.living.LivingBreatheEvent;

/**
 * Port-only: diving helmet breathing, on NeoForge's breathing hook instead of vanilla's.
 *
 * <p>Upstream wraps two calls in {@code LivingEntity.baseTick()}: {@code isEyeInFluid(WATER)} (to
 * spend backtank air while in lava) and {@code MobEffectUtil.hasWaterBreathing} (to let the helmet
 * stop drowning). NeoForge's patch guards that whole vanilla block with {@code if (false)} and moves
 * the logic to {@code CommonHooks.onLivingBreathe}, so javac drops both calls from the bytecode and
 * the wraps had zero targets -- a fatal injection failure at server start.
 *
 * <p>The conditions below reproduce when vanilla would have reached each wrapped call:
 * <ul>
 *   <li>water: the eye is in water, not in a bubble column, and the entity cannot otherwise breathe
 *       there. NeoForge folds all of that into {@code !canBreathe()}. As in vanilla, a helmet that
 *       works stops the air loss without refilling it.</li>
 *   <li>lava: the eye is not in water and the player is in lava. Upstream's wrap returned false
 *       here, so vanilla carried on with its normal "not in water" path; nothing else to change.</li>
 * </ul>
 * Invulnerable players are skipped, as upstream does; NeoForge already reports them as breathing.
 */
public final class BreathingHooks {
    private BreathingHooks() {
    }

    public static void onLivingBreathe(LivingBreatheEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.getAbilities().invulnerable) {
            return;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        boolean eyeInWater = player.isEyeInFluid(FluidTags.WATER);
        if (eyeInWater) {
            if (!event.canBreathe() && DivingHelmetItem.breatheUnderwater(player, level)) {
                event.setCanBreathe(true);
                event.setRefillAirAmount(0);
            }
        } else if (player.isInLava()) {
            DivingHelmetItem.breatheInLava(player, level);
        }
    }
}
