package com.zurrtum.create.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.zurrtum.create.foundation.block.SlipperinessControlBlock;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ExperienceOrb.class)
public abstract class ExperienceOrbMixin extends Entity {
    public ExperienceOrbMixin(EntityType<?> type, Level world) {
        super(type, world);
    }

    // Port note: upstream wraps Block.getFriction() here for SlipperinessControlBlock. NeoForge
    // patches this call to the level-sensitive IBlockExtension.getFriction, which
    // SlipperinessControlBlock now overrides, so the wrap has no target and is not needed.
}
