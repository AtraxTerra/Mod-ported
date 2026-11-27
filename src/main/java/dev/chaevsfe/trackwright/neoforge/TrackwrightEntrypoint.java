// Adapted from chaevsfe / Create Fly: Trackwright (https://github.com/chaevsfe/Trackwright)
// Licensed under GPL-3.0-or-later — extracted/adapted with AI assistance.
package dev.chaevsfe.trackwright.neoforge;

import com.zurrtum.create.platform.AddonEntrypoint;
import dev.chaevsfe.trackwright.Trackwright;

import java.lang.reflect.InvocationTargetException;

/**
 * Port: Create Fly's addon entrypoints in place of upstream's Fabric ones. {@link #onInitialize} runs
 * right after Create's registration, {@link #onInitializeClient} right after Create's client set-up in
 * Minecraft's constructor: the same moments Fabric gives upstream's {@code main} and {@code client}
 * entrypoints.
 *
 * <p>The client entrypoint is in the client source set, which common code cannot name at compile time
 * (upstream keeps the two apart, and so does this build), so it is reached by name.
 */
public final class TrackwrightEntrypoint implements AddonEntrypoint {
    private static final String CLIENT_ENTRYPOINT = "dev.chaevsfe.trackwright.client.TrackwrightClient";

    @Override
    public void onInitialize() {
        ServerClassProbe.runIfRequested();
        new Trackwright().onInitialize();
    }

    @Override
    public void onInitializeClient() {
        try {
            Class<?> type = Class.forName(CLIENT_ENTRYPOINT, true, TrackwrightEntrypoint.class.getClassLoader());
            Object client = type.getDeclaredConstructor().newInstance();
            type.getMethod("onInitializeClient").invoke(client);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException("Trackwright's client set-up failed", e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Trackwright cannot find its client entrypoint " + CLIENT_ENTRYPOINT, e);
        }
    }
}
