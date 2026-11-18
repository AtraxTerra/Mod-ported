// Adapted from chaevsfe / Create Fly: Diesel Generators (https://github.com/chaevsfe/Create-Diesel-Generators)
// Based on original work from Create: Diesel Generators by George VI (https://github.com/george8188625/Create-Diesel-Generators)
// Licensed under MIT — extracted/adapted with AI assistance.
package com.jesz.createdieselgenerators;

import com.jesz.createdieselgenerators.fuel_type.FuelType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;

@EventBusSubscriber(modid = CreateDieselGenerators.ID)
public class CDGRegistries {
    public static final ResourceKey<Registry<FuelType>> FUEL_TYPE = key("fuel_type");

    private static <T> ResourceKey<Registry<T>> key(String name) {
        return ResourceKey.createRegistryKey(CreateDieselGenerators.rl(name));
    }

    public static void register() {
        // Port: registered on DataPackRegistryEvent.NewRegistry (NeoForge), below.
    }

    @SubscribeEvent
    public static void onNewDataPackRegistry(DataPackRegistryEvent.NewRegistry event) {
        event.dataPackRegistry(FUEL_TYPE, FuelType.CODEC, FuelType.NCODEC);
    }
}
