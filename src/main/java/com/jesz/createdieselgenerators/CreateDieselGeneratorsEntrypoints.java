// Adapted from chaevsfe / Create Fly: Diesel Generators (https://github.com/chaevsfe/Create-Diesel-Generators)
// Based on original work from Create: Diesel Generators by George VI (https://github.com/george8188625/Create-Diesel-Generators)
// Licensed under MIT — extracted/adapted with AI assistance.
package com.jesz.createdieselgenerators;

import com.jesz.createdieselgenerators.client.CDGClient;
import com.zurrtum.create.platform.AddonEntrypoint;
import com.zurrtum.create.platform.LegacyBlockEntityData;

/**
 * The Fabric {@code main} and {@code client} entrypoints (CreateDieselGeneratorsFabric and
 * CreateDieselGeneratorsClient upstream), with the same bodies, called by Create Fly at the moments
 * Fabric would have called them. Declared in META-INF/services.
 */
public final class CreateDieselGeneratorsEntrypoints implements AddonEntrypoint {
    @Override
    public void onInitialize() {
        CreateDieselGeneratorsPlugin.verifyEarlyRegistrationComplete();
        CreateDieselGenerators.init();
        // port: worlds saved with "Create: Diesel Generators Adapted" (on Create: Adapted) store
        // block entities and entities in the original layout; Create Fly converts them on load.
        CDGLegacyData.register();
    }

    @Override
    public void onInitializeClient() {
        CDGConfig.registerClient();
        CDGClient.initClient();
    }
}
