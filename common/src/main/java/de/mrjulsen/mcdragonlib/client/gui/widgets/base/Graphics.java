package de.mrjulsen.mcdragonlib.client.gui.widgets.base;

import de.mrjulsen.mcdragonlib.client.util.GuiPoseStack;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** NeoForge 26.2 port: GuiGraphics is GuiGraphicsExtractor, and the 2D pose is a {@link GuiPoseStack}. */
public record Graphics(GuiGraphicsExtractor graphics, GuiPoseStack poseStack, float partialTick) {}
