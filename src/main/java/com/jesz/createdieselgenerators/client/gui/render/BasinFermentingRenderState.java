// Adapted from chaevsfe / Create Fly: Diesel Generators (https://github.com/chaevsfe/Create-Diesel-Generators)
// Based on original work from Create: Diesel Generators by George VI (https://github.com/george8188625/Create-Diesel-Generators)
// Licensed under MIT — extracted/adapted with AI assistance.
package com.jesz.createdieselgenerators.client.gui.render;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;

public record BasinFermentingRenderState(Matrix3x2f pose, int x0, int y0,
                                         ScreenRectangle bounds) implements PictureInPictureRenderState {
    public BasinFermentingRenderState(Matrix3x2f pose, int x, int y) {
        this(pose, x, y, new ScreenRectangle(x, y, 30, 80).transformMaxBounds(pose));
    }

    @Override
    public int x1() {
        return x0 + 30;
    }

    @Override
    public int y1() {
        return y0 + 80;
    }

    @Override
    public float scale() {
        return 23;
    }

    @Override
    public @Nullable ScreenRectangle scissorArea() {
        return null;
    }
}
