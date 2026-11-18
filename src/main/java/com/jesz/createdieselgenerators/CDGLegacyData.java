// Adapted from chaevsfe / Create Fly: Diesel Generators (https://github.com/chaevsfe/Create-Diesel-Generators)
// Based on original work from Create: Diesel Generators by George VI (https://github.com/george8188625/Create-Diesel-Generators)
// Licensed under MIT — extracted/adapted with AI assistance.
package com.jesz.createdieselgenerators;

import com.zurrtum.create.platform.LegacyBlockEntityData;

/**
 * Port-only: worlds saved with "Create: Diesel Generators Adapted" (the NeoForge 26.2 build of the
 * original mod, on Create: Adapted) keep its block entities in the original layout. Create Fly's
 * {@link LegacyBlockEntityData} applies its shared rewrites (positions, UUIDs, directions, tanks in
 * millibuckets, inventories) to this mod's namespace too; per-type rewrites are added here as the
 * migration test finds them.
 */
public final class CDGLegacyData {
    private CDGLegacyData() {
    }

    public static void register() {
        LegacyBlockEntityData.registerNamespace(CreateDieselGenerators.ID);
    }
}
