// Adapted from chaevsfe / Create Fly: Diesel Generators (https://github.com/chaevsfe/Create-Diesel-Generators)
// Based on original work from Create: Diesel Generators by George VI (https://github.com/george8188625/Create-Diesel-Generators)
// Licensed under MIT — extracted/adapted with AI assistance.
package com.jesz.createdieselgenerators.client.model;

import com.google.common.base.Suppliers;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.minecraft.client.renderer.block.dispatch.BlockModelRotation;
import net.minecraft.client.renderer.item.CuboidItemModelWrapper;
import net.minecraft.client.renderer.item.ModelRenderProperties;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.resources.Identifier;
import org.joml.Vector3fc;

import java.util.List;
import java.util.function.Supplier;

public record ItemModelParts(List<BakedQuad> quads, ModelRenderProperties properties) {
    public static ItemModelParts of(ModelBaker baker, Identifier model) {
        var resolved = baker.getModel(model);
        var slots = resolved.getTopTextureSlots();
        var quads = resolved.bakeTopGeometry(slots, baker, BlockModelRotation.IDENTITY).getAll();
        return new ItemModelParts(quads, ModelRenderProperties.fromResolvedModel(baker, resolved, slots));
    }

    public static List<BakedQuad> quads(ModelBaker baker, Identifier model) {
        var resolved = baker.getModel(model);
        return resolved.bakeTopGeometry(resolved.getTopTextureSlots(), baker, BlockModelRotation.IDENTITY).getAll();
    }

    public static Supplier<Vector3fc[]> extents(List<BakedQuad> quads) {
        return Suppliers.memoize(() -> CuboidItemModelWrapper.computeExtents(quads));
    }
}
