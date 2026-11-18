package com.zurrtum.create.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.storage.TagValueInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Port-only: the tag behind a ValueInput, so legacy entity data can be rewritten before it is read. */
@Mixin(TagValueInput.class)
public interface TagValueInputAccessor {
    @Accessor("input")
    CompoundTag getCreateLegacyInput();
}
