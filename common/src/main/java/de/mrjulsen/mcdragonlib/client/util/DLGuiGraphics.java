package de.mrjulsen.mcdragonlib.client.util;

import org.joml.Matrix3x2fStack;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Everything a DragonLib widget draws with.
 *
 * <p>NeoForge 26.2 port: the GUI extractor ({@link GuiGraphicsExtractor}) replaces GuiGraphics, and its
 * 2D matrix stack is reached through {@link GuiPoseStack}, which keeps the PoseStack calls the widgets
 * use. There are no immediate-mode buffers any more (1.21's {@code bufferSource()} and
 * {@code vertexConsumer()}): custom geometry goes through {@link GuiVertexRecorder}.
 */
public record DLGuiGraphics(GuiGraphicsExtractor graphics, GuiPoseStack poseStack, Font defaultFont, float partialTick) {

    /** Same call as 1.21's {@code new DLGuiGraphics(guiGraphics, guiGraphics.pose(), font, partialTick)}. */
    public DLGuiGraphics(GuiGraphicsExtractor graphics, Matrix3x2fStack pose, Font defaultFont, float partialTick) {
        this(graphics, new GuiPoseStack(pose), defaultFont, partialTick);
    }

    public DLGuiGraphics(GuiGraphicsExtractor graphics, Font defaultFont, float partialTick) {
        this(graphics, graphics.pose(), defaultFont, partialTick);
    }
}
