// Adapted from chaevsfe / Create Fly: Diesel Generators (https://github.com/chaevsfe/Create-Diesel-Generators)
// Based on original work from Create: Diesel Generators by George VI (https://github.com/george8188625/Create-Diesel-Generators)
// Licensed under MIT — extracted/adapted with AI assistance.
package com.jesz.createdieselgenerators.events;

import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import com.jesz.createdieselgenerators.CDGConfig;
import com.jesz.createdieselgenerators.CDGItems;
import com.jesz.createdieselgenerators.CDGRegistries;
import com.jesz.createdieselgenerators.commands.CDGCommands;
import com.jesz.createdieselgenerators.content.entity_filter.ReverseLootTable;
import com.jesz.createdieselgenerators.fuel_type.FuelType;
import com.jesz.createdieselgenerators.mixins.LootItemAccessor;
import com.jesz.createdieselgenerators.mixins.LootPoolAccessor;
import com.jesz.createdieselgenerators.mixins.LootTableAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GameEvents {
    private static final Map<Level, Set<BlockPos>> toExplode = new HashMap<>();

    public static void register() {
        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, event -> new CDGCommands(event.getDispatcher()));
        // Port: NeoForge loads every loot table (LootTableLoadEvent, per table) before it posts
        // AddServerReloadListenersEvent, so indexing here sees the complete set, like ALL_LOADED.
        NeoForge.EVENT_BUS.addListener(AddServerReloadListenersEvent.class, event -> {
            ReverseLootTable.ALL.clear();
            event.getServerResources().fullRegistries().lookup().lookupOrThrow(Registries.LOOT_TABLE).listElements()
                .forEach(holder -> indexLootTable(holder.key().identifier(), holder.value()));
        });
        NeoForge.EVENT_BUS.addListener(LevelTickEvent.Post.class, event -> {
            if (event.getLevel() instanceof ServerLevel level)
                onServerTick(level);
        });
        // Port: wood chips' fuel value is data on NeoForge (FuelValues come from the
        // neoforge:furnace_fuels data map): data/neoforge/data_maps/item/furnace_fuels.json.
    }

    private static void indexLootTable(Identifier tableId, LootTable table) {
        if (tableId == null || !tableId.getPath().startsWith("entities/"))
            return;
        String path = tableId.getPath().replaceAll("entities/", "");
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.fromNamespaceAndPath(tableId.getNamespace(), path));
        ((LootTableAccessor) table).getPools().forEach(pool -> {
            for (LootPoolEntryContainer entry : ((LootPoolAccessor) pool).getEntries())
                if (entry instanceof LootItemAccessor lootItem) {
                    List<EntityType<?>> types = ReverseLootTable.ALL.computeIfAbsent(lootItem.getItem().value(), s -> new ArrayList<>());
                    if (!types.contains(type))
                        types.add(type);
                }
        });
    }

    private static void onServerTick(ServerLevel level) {
        Set<BlockPos> pending = toExplode.get(level);
        if (pending == null || pending.isEmpty())
            return;
        for (BlockPos pos : List.copyOf(pending)) {
            level.explode(null, null, null, pos.getX(), pos.getY(), pos.getZ(), 1, true, Level.ExplosionInteraction.BLOCK);
            pending.remove(pos);
        }
        if (pending.isEmpty())
            toExplode.remove(level);
    }

    public static void onExplosion(Level level, double centerX, double centerY, double centerZ) {
        if (!CDGConfig.server().COMBUSTIBLES_BLOW_UP.get() || level.isClientSide())
            return;
        for (int x = -2; x < 2; x++)
            for (int y = -2; y < 2; y++)
                for (int z = -2; z < 2; z++) {
                    BlockPos pos = new BlockPos((int) (x + centerX), (int) (y + centerY), (int) (z + centerZ));
                    if (!level.isInWorldBounds(pos))
                        continue;
                    if (Math.abs(Math.sqrt(x * x + y * y + z * z)) >= 2)
                        continue;
                    FluidState fluidState = level.getFluidState(pos);
                    boolean flammable = FuelType.getTypeFor(level.registryAccess().lookupOrThrow(CDGRegistries.FUEL_TYPE), fluidState.getType()).normal().speed() != 0;
                    if (!flammable)
                        continue;
                    level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                    toExplode.computeIfAbsent(level, l -> new HashSet<>()).add(pos);
                    return;
                }
    }
}
