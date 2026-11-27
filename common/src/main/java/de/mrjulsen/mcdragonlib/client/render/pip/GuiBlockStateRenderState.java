package de.mrjulsen.mcdragonlib.client.render.pip;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.world.level.block.state.BlockState;

/**
 * NeoForge 26.2 port: a block model drawn into a GUI box (1.21 drew it straight into the GUI buffers).
 * The box's centre is the origin the 1.21 code translated to, {@code scale} the pixels per block, and
 * {@code yRot} the rotation it applied; {@link GuiBlockStateRenderer} reproduces that transform.
 */
public record GuiBlockStateRenderState(
    BlockState state,
    float yRot,
    int x0,
    int y0,
    int x1,
    int y1,
    float scale,
    @Nullable ScreenRectangle scissorArea,
    @Nullable ScreenRectangle bounds
) implements PictureInPictureRenderState {

    public GuiBlockStateRenderState(BlockState state, float yRot, int x0, int y0, int x1, int y1, float scale, @Nullable ScreenRectangle scissorArea) {
        this(state, yRot, x0, y0, x1, y1, scale, scissorArea, PictureInPictureRenderState.getBounds(x0, y0, x1, y1, scissorArea));
    }
}
