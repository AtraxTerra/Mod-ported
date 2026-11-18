package com.zurrtum.create.client.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.datafixers.util.Pair;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.client.resources.model.cuboid.CuboidModel;
import net.minecraft.resources.Identifier;
import net.minecraft.util.GsonHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.Reader;

@Mixin(ModelManager.class)
public class LoadBlockModelMixin {
    // Port note: upstream swaps vanilla's CuboidModel.fromStream(reader) for
    // GsonHelper.fromJson(CuboidModel.GSON, reader, UnbakedModel.class). NeoForge's patch makes the
    // same swap, to UnbakedModelParser.parse(reader) -- whose body is that exact line. The mixin
    // reimplemented NeoForge's own patch for Fabric and has nothing to add here.
}
