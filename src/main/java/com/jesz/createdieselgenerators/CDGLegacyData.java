// Adapted from chaevsfe / Create Fly: Diesel Generators (https://github.com/chaevsfe/Create-Diesel-Generators)
// Based on original work from Create: Diesel Generators by George VI (https://github.com/george8188625/Create-Diesel-Generators)
// Licensed under MIT — extracted/adapted with AI assistance.
package com.jesz.createdieselgenerators;

import com.zurrtum.create.platform.LegacyBlockEntityData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/**
 * Port-only: worlds saved with "Create: Diesel Generators Adapted" (the NeoForge 26.2 build of the
 * original mod, on Create: Adapted) keep this mod's block entities in the original layout. Create
 * Fly's {@link LegacyBlockEntityData} applies its shared rewrites (positions, UUIDs, smart tanks in
 * millibuckets, ...) to this namespace too; the rewrites below are this mod's own, found by the
 * migration test in CI:
 * <ul>
 *   <li>oil barrels, distillation tanks, modular engines: {@code TankContent} held a bare fluid
 *   stack in mB; here it is a tank, {@code TankContent:{Fluid:{...}}}, in droplets;</li>
 *   <li>bulk fermenters: the same fluid goes into the first of its tanks
 *   ({@code TankContent:{Tanks:[...]}}), and its item handler ({@code Inventory:{Size, Items}})
 *   becomes {@code Inventory:{Inventory:[...]}}.</li>
 * </ul>
 * Each keys on the original shape (a fluid {@code id} right inside {@code TankContent}, a handler's
 * {@code Items} list), so data this port saved is left alone.
 */
public final class CDGLegacyData {
    private CDGLegacyData() {
    }

    public static void register() {
        LegacyBlockEntityData.registerNamespace(CreateDieselGenerators.ID);
        for (String id : new String[]{"oil_barrel_block_entity", "distillation_tank_block_entity", "large_diesel_engine_tile_entity"}) {
            LegacyBlockEntityData.registerFixer(CreateDieselGenerators.ID + ":" + id, CDGLegacyData::singleTank);
        }
        LegacyBlockEntityData.registerFixer(CreateDieselGenerators.ID + ":bulk_fermenter", CDGLegacyData::bulkFermenter);
    }

    private static boolean legacyTank(CompoundTag tag) {
        return tag.get("TankContent") instanceof CompoundTag tank && tank.contains("id");
    }

    private static void singleTank(CompoundTag tag) {
        if (legacyTank(tag)) {
            CompoundTag tank = new CompoundTag();
            CompoundTag fluid = LegacyBlockEntityData.fluidInDroplets(tag.get("TankContent"));
            if (fluid != null) {
                tank.put("Fluid", fluid);
            }
            tag.put("TankContent", tank);
        }
    }

    private static void bulkFermenter(CompoundTag tag) {
        if (legacyTank(tag)) {
            CompoundTag first = new CompoundTag();
            CompoundTag fluid = LegacyBlockEntityData.fluidInDroplets(tag.get("TankContent"));
            if (fluid != null) {
                first.put("Fluid", fluid);
            }
            ListTag tanks = new ListTag();
            tanks.add(first);
            CompoundTag content = new CompoundTag();
            content.put("Tanks", tanks);
            tag.put("TankContent", content);
        }
        if (tag.get("Inventory") instanceof CompoundTag inventory && inventory.get("Items") instanceof ListTag) {
            CompoundTag fixed = new CompoundTag();
            fixed.put("Inventory", LegacyBlockEntityData.compactStacks(inventory));
            tag.put("Inventory", fixed);
        }
    }
}
