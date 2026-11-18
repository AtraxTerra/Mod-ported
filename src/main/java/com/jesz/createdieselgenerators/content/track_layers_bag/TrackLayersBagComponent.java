// Adapted from chaevsfe / Create Fly: Diesel Generators (https://github.com/chaevsfe/Create-Diesel-Generators)
// Based on original work from Create: Diesel Generators by George VI (https://github.com/george8188625/Create-Diesel-Generators)
// Licensed under MIT — extracted/adapted with AI assistance.
package com.jesz.createdieselgenerators.content.track_layers_bag;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.item.ItemStack;

public record TrackLayersBagComponent(ItemStack stack) implements ClientTooltipComponent {
    @Override
    public int getHeight(Font font) {
        return 20;
    }

    @Override
    public int getWidth(Font font) {
        return (int) Math.ceil((double) stack.getCount() / 64) * 10 + 10;
    }

    @Override
    public void extractImage(Font font, int x, int y, int width, int height, GuiGraphicsExtractor graphics) {
        for (int i = 0; i < stack.getCount(); i += 64)
            graphics.item(stack, (int) (x + i / 6.4), y - (i % 5) / 2 + 1);
        int last = x + (int) Math.ceil((float) stack.getCount() / 64) * 10 - 10;
        graphics.item(stack, last, y);
        graphics.itemDecorations(font, stack, last, y);
    }
}
