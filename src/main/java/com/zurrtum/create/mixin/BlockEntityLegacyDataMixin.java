package com.zurrtum.create.mixin;

import com.zurrtum.create.platform.LegacyBlockEntityData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Port-only: block entities saved by the original Create / Create: Adapted are rewritten into
 * Create Fly's layout before they load. See {@link LegacyBlockEntityData}.
 */
@Mixin(BlockEntity.class)
public class BlockEntityLegacyDataMixin {
    @ModifyVariable(method = "loadStatic(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/world/level/block/entity/BlockEntity;", at = @At("HEAD"), argsOnly = true)
    private static CompoundTag create$upgradeLegacyData(CompoundTag tag) {
        return LegacyBlockEntityData.upgrade(tag);
    }
}
