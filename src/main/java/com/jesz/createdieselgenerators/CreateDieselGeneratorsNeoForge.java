// Adapted from chaevsfe / Create Fly: Diesel Generators (https://github.com/chaevsfe/Create-Diesel-Generators)
// Based on original work from Create: Diesel Generators by George VI (https://github.com/george8188625/Create-Diesel-Generators)
// Licensed under MIT — extracted/adapted with AI assistance.
package com.jesz.createdieselgenerators;

import net.neoforged.fml.common.Mod;

/**
 * NeoForge mod class. Deliberately empty: on Fabric, Diesel Generators registers its content from a
 * {@code ModInitializer} straight into vanilla registries and starts its client side from inside
 * {@code Minecraft}'s constructor. The Create Fly NeoForge port runs addons at exactly those two
 * moments through {@link com.zurrtum.create.platform.AddonEntrypoint}; see
 * {@link CreateDieselGeneratorsEntrypoints}. NeoForge events are handled by
 * {@code @EventBusSubscriber} classes next to the code they serve.
 */
@Mod(CreateDieselGenerators.ID)
public final class CreateDieselGeneratorsNeoForge {
    public CreateDieselGeneratorsNeoForge() {
    }
}
