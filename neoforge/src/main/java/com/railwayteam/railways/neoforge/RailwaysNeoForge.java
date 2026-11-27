// Adapted from chaevsfe / Create Fly: Steam 'n' Rails (https://github.com/chaevsfe/Create-Steam-n-Rails-Fly)
// Based on original work from Create: Steam 'n' Rails by The Railways Team (https://github.com/Layers-of-Railways/Railway)
// Licensed under LGPL-3.0-only — extracted/adapted with AI assistance.
package com.railwayteam.railways.neoforge;

import com.railwayteam.railways.Railways;
import net.neoforged.fml.common.Mod;

/**
 * NeoForge mod class. Deliberately empty, like the Diesel Generators port's.
 *
 * <p>On Fabric, Steam 'n' Rails registers its content from Create Fly's {@code create_plugin}
 * entrypoint and its {@code ModInitializer}, straight into vanilla registries, and starts its client
 * side from {@code ClientModInitializer}, inside {@code Minecraft}'s constructor. The Create Fly
 * NeoForge port runs addons at exactly those moments through
 * {@link com.zurrtum.create.api.registry.CreateRegisterPlugin} and
 * {@link com.zurrtum.create.platform.AddonEntrypoint}, both declared in META-INF/services; see
 * {@link RailwaysEntrypoints}. That happens before FML constructs this class, so nothing here may be
 * needed by registration. NeoForge events are handled by the {@code @EventBusSubscriber} classes of
 * this package ({@link RailwaysNeoForgeEvents}, {@link RailwaysNeoForgeClientEvents}).
 */
@Mod(Railways.MOD_ID)
public final class RailwaysNeoForge {
    public RailwaysNeoForge() {
    }
}
