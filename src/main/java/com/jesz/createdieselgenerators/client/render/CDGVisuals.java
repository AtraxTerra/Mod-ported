// Adapted from chaevsfe / Create Fly: Diesel Generators (https://github.com/chaevsfe/Create-Diesel-Generators)
// Based on original work from Create: Diesel Generators by George VI (https://github.com/george8188625/Create-Diesel-Generators)
// Licensed under MIT — extracted/adapted with AI assistance.
package com.jesz.createdieselgenerators.client.render;

import com.jesz.createdieselgenerators.registrate.fn.BlockEntityVisualFactory;
import com.zurrtum.create.client.content.kinetics.base.SingleAxisRotatingVisual;
import com.zurrtum.create.client.flywheel.lib.model.baked.PartialModel;
import com.zurrtum.create.client.flywheel.lib.visualization.SimpleBlockEntityVisualizer;
import com.zurrtum.create.content.kinetics.base.KineticBlockEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

public final class CDGVisuals {
    private CDGVisuals() {
    }

    public static <T extends KineticBlockEntity> BlockEntityVisualFactory<T> singleAxisRotating(PartialModel model) {
        SimpleBlockEntityVisualizer.Factory<T> factory = SingleAxisRotatingVisual.of(model);
        return factory::create;
    }
}
