// Adapted from chaevsfe / Create Fly: Diesel Generators (https://github.com/chaevsfe/Create-Diesel-Generators)
// Based on original work from Create: Diesel Generators by George VI (https://github.com/george8188625/Create-Diesel-Generators)
// Licensed under MIT — extracted/adapted with AI assistance.
package com.jesz.createdieselgenerators;

import com.zurrtum.create.platform.Platform;
import com.jesz.createdieselgenerators.compat.CDGRecipeSync;
import com.jesz.createdieselgenerators.content.molds.MoldType;
import com.jesz.createdieselgenerators.events.GameEvents;
import com.jesz.createdieselgenerators.packets.CDGPackets;
import com.jesz.createdieselgenerators.registrate.CDGRegistrate;
import com.zurrtum.create.compat.Mods;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.minecraft.world.entity.EntityTypes;

public class CreateDieselGenerators {
    public static final String ID = "createdieselgenerators";
    public static final Logger LOGGER = LoggerFactory.getLogger(CreateDieselGenerators.class);

    public static final CDGRegistrate REGISTRATE = CDGRegistrate.create(ID);

    public static void registerBlocksEarly() {
        CDGDisplaySources.register();
        CDGBlocks.register();
        // Port: Strut Your Stuff has no NeoForge build; its compat (compat/strut_your_stuff) is excluded.
        com.jesz.createdieselgenerators.registrate.Registrate.registerFluidBlocks();
    }

    public static void registerFluidsEarly() {
        CDGFluids.register();
    }

    public static void init() {
        CDGConfig.register();

        com.jesz.createdieselgenerators.registrate.Registrate.registerFluidItems();
        CDGItems.register();
        CDGBlockEntityTypes.register();
        CDGEntityTypes.register();
        CDGSoundEvents.register();
        CDGRecipes.register();
        CDGRecipeSync.register();
        CDGMenuTypes.register();
        MoldType.register();
        CDGMountedStorageTypes.register();
        CDGCreativeTab.register();
        CDGPackets.register();
        CDGDataComponents.register();
        CDGRegistries.register();

        GameEvents.register();
        com.jesz.createdieselgenerators.events.ModEvents.setup();
        REGISTRATE.register();
    }

    public static Identifier rl(String path) {
        return Identifier.fromNamespaceAndPath(ID, path);
    }

    public static Component lang(String path, Object... args) {
        return Component.translatable(ID + "." + path, args);
    }
}
