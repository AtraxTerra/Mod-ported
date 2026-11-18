// Adapted from chaevsfe / Create Fly: Diesel Generators (https://github.com/chaevsfe/Create-Diesel-Generators)
// Based on original work from Create: Diesel Generators by George VI (https://github.com/george8188625/Create-Diesel-Generators)
// Licensed under MIT — extracted/adapted with AI assistance.
package com.jesz.createdieselgenerators.compat.strut_your_stuff;

import com.zurrtum.create.client.foundation.item.ItemDescription;
import com.cake.struts.compat.flywheel.StrutsFlywheelCompatLoader;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

public final class StrutYourStuffClient {
    private StrutYourStuffClient() {
    }

    public static void register() {
        ItemDescription.useKey(StrutYourStuffRegistryEntries.ANDESITE_GIRDER_STRUT.get(), "block.bits_n_bobs.girder_strut");
        StrutsFlywheelCompatLoader.registerStrutVisual(StrutYourStuffBlockEntityTypes.ANDESITE_GIRDER_STRUT.get());
    }
}
