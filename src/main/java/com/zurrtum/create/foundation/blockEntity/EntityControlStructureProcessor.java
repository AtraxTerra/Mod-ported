package com.zurrtum.create.foundation.blockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Port note: upstream's StructureTemplateMixin filtered the entities StructureTemplate places
 * through every processor implementing this interface, using {@code skip}. NeoForge replaces
 * vanilla's placeEntities with addEntitiesToWorld, which runs each processor's
 * {@code processEntity} first and drops entities it returns null for -- the same filter, natively.
 * Upstream applied it only when placing into a real Level; so does this.
 */
public interface EntityControlStructureProcessor extends StructureProcessor {
    boolean skip(Level world, StructureTemplate.StructureEntityInfo info);

    @Override
    default StructureTemplate.StructureEntityInfo processEntity(
        LevelReader world,
        BlockPos seedPos,
        StructureTemplate.StructureEntityInfo rawEntityInfo,
        StructureTemplate.StructureEntityInfo entityInfo,
        StructurePlaceSettings placementSettings,
        StructureTemplate template
    ) {
        if (world instanceof Level level && skip(level, entityInfo)) {
            return null;
        }
        return entityInfo;
    }
}
