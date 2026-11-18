// Adapted from chaevsfe / Create Fly: Diesel Generators (https://github.com/chaevsfe/Create-Diesel-Generators)
// Based on original work from Create: Diesel Generators by George VI (https://github.com/george8188625/Create-Diesel-Generators)
// Licensed under MIT — extracted/adapted with AI assistance.
package com.jesz.createdieselgenerators;

import com.jesz.createdieselgenerators.client.CDGClient;
import net.fabricmc.api.ClientModInitializer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

public final class CreateDieselGeneratorsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        CDGConfig.registerClient();
        CDGClient.initClient();
    }
}
