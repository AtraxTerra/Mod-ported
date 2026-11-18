package com.zurrtum.create.mixin;

import com.zurrtum.create.platform.LegacyEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.ValueInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Port-only: Create entities saved by the original Create / Create: Adapted (contraptions above
 * all) are rewritten into Create Fly's layout before they load. See {@link LegacyEntityData}.
 */
@Mixin(Entity.class)
public class EntityLegacyDataMixin {
    @Inject(method = "load(Lnet/minecraft/world/level/storage/ValueInput;)V", at = @At("HEAD"))
    private void create$upgradeLegacyData(ValueInput input, CallbackInfo ci) {
        if (input instanceof TagValueInput tagInput && "create".equals(EntityType.getKey(((Entity) (Object) this).getType())
            .getNamespace())) {
            LegacyEntityData.upgrade(((TagValueInputAccessor) tagInput).getCreateLegacyInput());
        }
    }
}
