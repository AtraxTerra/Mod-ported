package com.zurrtum.create.platform;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

/**
 * Port-only: registry aliases so worlds saved by the original Create (and Create: Adapted, which
 * runs the original Create's content on NeoForge 26.2) load under Create Fly.
 *
 * <p>Block, item, fluid, entity and block-entity ids were compared against Create 6.0.10 (the asset
 * source Create: Adapted requires). Everything matches except:
 * <ul>
 *   <li>block entity {@code create:simple_kinetic} (shafts, cogwheels, large cogwheels) is
 *       {@code create:bracketed_kinetic} here. Without the alias every shaft and cogwheel would load
 *       without its block entity, dropping any bracket on it;</li>
 *   <li>item {@code create:copper_nugget} is gone: vanilla has had {@code minecraft:copper_nugget}
 *       since 1.21.9, and Create Fly uses it. Stored stacks are redirected instead of vanishing.</li>
 * </ul>
 * {@code create:refined_radiance} and {@code create:shadow_steel} (legacy items) have no counterpart
 * and are not aliased. NeoForge resolves aliases on every registry lookup, including the ones that
 * load chunks and item stacks.
 */
public final class LegacyCreateIds {
    private LegacyCreateIds() {
    }

    public static void register() {
        BuiltInRegistries.BLOCK_ENTITY_TYPE.addAlias(id("create", "simple_kinetic"), id("create", "bracketed_kinetic"));
        BuiltInRegistries.ITEM.addAlias(id("create", "copper_nugget"), id("minecraft", "copper_nugget"));
    }

    private static Identifier id(String namespace, String path) {
        return Identifier.fromNamespaceAndPath(namespace, path);
    }
}
