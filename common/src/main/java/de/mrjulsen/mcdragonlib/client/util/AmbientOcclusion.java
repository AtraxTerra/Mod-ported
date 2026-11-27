package de.mrjulsen.mcdragonlib.client.util;

import com.mojang.blaze3d.vertex.QuadInstance;
import net.minecraft.client.renderer.FaceInfo;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockModelLighter;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

/**
 * NeoForge 26.2 port: per-corner brightness and light for a block face, used by
 * {@link RenderUtils#addQuadSide} when a renderer asks for ambient occlusion.
 *
 * <p>1.21 asked {@code ModelBlockRenderer.AmbientOcclusionFace} for them, passing an empty shape: the
 * face was treated as a full face inside the block (not flush with its boundary), lit from the
 * block's own position, then shaded by the face direction. 26.2 moved ambient occlusion into
 * {@link BlockModelLighter}, which reads the face shape from a baked quad instead of a shape array,
 * so this hands it a full face through the middle of the block: the same inner face. One difference
 * comes from the lighter itself: for a block whose collision shape is a full cube it treats any full
 * face as an outer one and lights it from the neighbour in front, as it does for vanilla faces.
 *
 * <p>The corner order is the lighter's output order (the vertex order of {@link FaceInfo}), the order
 * 1.21's {@code brightness} and {@code lightmap} arrays used.
 */
final class AmbientOcclusion {

    /** Only {@code shade} is read by the lighter; a shaded face, as upstream asked for. */
    private static final BakedQuad.MaterialInfo MATERIAL = new BakedQuad.MaterialInfo(null, ChunkSectionLayer.SOLID, null, -1, true, 0);
    private static final BakedQuad[] FACES = new BakedQuad[Direction.values().length];
    private static final ThreadLocal<BlockModelLighter> LIGHTER = ThreadLocal.withInitial(BlockModelLighter::new);
    private static final ThreadLocal<QuadInstance> OUTPUT = ThreadLocal.withInitial(QuadInstance::new);

    static {
        Vector3f min = new Vector3f(0.0F, 0.0F, 0.0F);
        Vector3f max = new Vector3f(1.0F, 1.0F, 1.0F);
        for (Direction direction : Direction.values()) {
            FaceInfo info = FaceInfo.fromFacing(direction);
            Vector3f[] corners = new Vector3f[4];
            for (int i = 0; i < 4; i++) {
                Vector3f corner = info.getVertexInfo(i).select(min, max);
                // Onto the middle plane of the block: a full face that is not on the block boundary.
                switch (direction.getAxis()) {
                    case X -> corner.x = 0.5F;
                    case Y -> corner.y = 0.5F;
                    case Z -> corner.z = 0.5F;
                }
                corners[i] = corner;
            }
            FACES[direction.get3DDataValue()] = new BakedQuad(corners[0], corners[1], corners[2], corners[3], 0L, 0L, 0L, 0L, direction, MATERIAL);
        }
    }

    private AmbientOcclusion() {}

    static void calculate(BlockAndTintGetter level, BlockState state, BlockPos pos, Direction direction, float[] brightness, int[] lightmap) {
        QuadInstance output = OUTPUT.get();
        LIGHTER.get().prepareQuadAmbientOcclusion(level, state, pos, FACES[direction.get3DDataValue()], output);
        for (int i = 0; i < 4; i++) {
            // The lighter writes grey colours (ambient occlusion times the directional shade).
            brightness[i] = ARGB.red(output.getColor(i)) / 255.0F;
            lightmap[i] = output.getLightCoords(i);
        }
    }
}
