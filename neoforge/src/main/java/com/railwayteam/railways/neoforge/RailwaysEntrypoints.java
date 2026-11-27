// Adapted from chaevsfe / Create Fly: Steam 'n' Rails (https://github.com/chaevsfe/Create-Steam-n-Rails-Fly)
// Based on original work from Create: Steam 'n' Rails by The Railways Team (https://github.com/Layers-of-Railways/Railway)
// Licensed under LGPL-3.0-only — extracted/adapted with AI assistance.
package com.railwayteam.railways.neoforge;

import com.railwayteam.railways.fabric.RailwaysClientImpl;
import com.railwayteam.railways.fabric.RailwaysImpl;
import com.zurrtum.create.platform.AddonEntrypoint;

/**
 * The Fabric {@code main} and {@code client} entrypoints ({@link RailwaysImpl} and
 * {@link RailwaysClientImpl} upstream), with the same bodies, called by Create Fly at the moments
 * Fabric would have called them. Declared in META-INF/services.
 */
public final class RailwaysEntrypoints implements AddonEntrypoint {
    @Override
    public void onInitialize() {
        ServerClassProbe.runIfRequested();
        new RailwaysImpl().onInitialize();
    }

    @Override
    public void onInitializeClient() {
        new RailwaysClientImpl().onInitializeClient();
    }
}
