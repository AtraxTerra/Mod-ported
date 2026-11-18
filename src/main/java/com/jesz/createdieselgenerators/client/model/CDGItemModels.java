// Adapted from chaevsfe / Create Fly: Diesel Generators (https://github.com/chaevsfe/Create-Diesel-Generators)
// Based on original work from Create: Diesel Generators by George VI (https://github.com/george8188625/Create-Diesel-Generators)
// Licensed under MIT — extracted/adapted with AI assistance.
package com.jesz.createdieselgenerators.client.model;

import com.zurrtum.create.client.AllModels;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

public final class CDGItemModels {
    private CDGItemModels() {
    }

    public static void register() {
        AllModels.register(ChemicalSprayerModel.ID, ChemicalSprayerModel.Unbaked.CODEC);
        AllModels.register(ChemicalSprayerModel.LIGHTER_ID, ChemicalSprayerModel.Unbaked.LIGHTER_CODEC);
        AllModels.register(HammerModel.ID, HammerModel.Unbaked.CODEC);
        AllModels.register(WireCuttersModel.ID, WireCuttersModel.Unbaked.CODEC);
        AllModels.register(MoldModel.ID, MoldModel.Unbaked.CODEC);
        AllModels.register(LighterItemModel.ID, LighterItemModel.Unbaked.CODEC);
    }
}
