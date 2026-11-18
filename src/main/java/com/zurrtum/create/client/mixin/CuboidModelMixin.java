package com.zurrtum.create.client.mixin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.zurrtum.create.client.model.UnbakedModelParser;
import net.minecraft.client.resources.model.cuboid.CuboidModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(CuboidModel.class)
public class CuboidModelMixin {
    // Port note -- removed deliberately, and the most consequential client change in the port.
    // 
    // Upstream replaces CuboidModel.GSON wholesale with one built around Create's own copy of
    // NeoForge's UnbakedModelParser, so that Create's 133 "loader": "neoforge:obj" models load
    // through Create's own port of NeoForge's OBJ loader. That is how Create Fly gets OBJ models on
    // Fabric, where no such loader exists.
    // 
    // On NeoForge it is both redundant and harmful. NeoForge already patches this GSON to register its
    // own UnbakedModel deserializer, which dispatches "loader" fields -- neoforge:obj included -- to the
    // loaders registered through ModelEvent.RegisterLoaders. Replacing the GSON shadowed that, and with
    // fabric-model-loading-api absent Create's deserializer fell back to parsing everything else as a
    // plain CuboidModel: every OTHER mod's custom-loader model would have lost its loader on the client.
    // 
    // Create's models were authored for NeoForge's loader -- that is literally what the JSON says -- so
    // they now load through it, as upstream Create does. Create's copies (client.model.UnbakedModelParser
    // and client.model.obj.*) are left in the tree, unwired, so this is one line to revert if NeoForge's
    // OBJ output turns out to differ from what Create's rendering expects. Trains, curved tracks and
    // other OBJ models are the first thing to look at in a real client.
}
