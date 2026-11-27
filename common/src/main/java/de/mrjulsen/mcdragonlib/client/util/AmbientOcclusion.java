package de.mrjulsen.mcdragonlib.client.util;

import net.minecraft.util.LightCoordsUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * NeoForge 26.2 port: per-corner brightness and light for a block face, used by
 * {@link RenderUtils#addQuadSide} when a renderer asks for ambient occlusion. 1.21 read these from
 * {@code ModelBlockRenderer.AmbientOcclusionFace}, which 26.2 removed.
 *
 * <p>First pass: every corner gets the light of the block in front of the face and full brightness,
 * the shading the same quad gets without ambient occlusion but lit from its neighbour. The 26.2 block
 * lighter will replace this once its API is wired in.
 */
final class AmbientOcclusion {
    private AmbientOcclusion() {}

    static void calculate(BlockAndTintGetter level, BlockState state, BlockPos pos, Direction direction, float[] brightness, int[] lightmap) {
        int light = LightCoordsUtil.getLightCoords(level, pos.relative(direction));
        for (int i = 0; i < 4; i++) {
            brightness[i] = 1.0F;
            lightmap[i] = light;
        }
    }
}
