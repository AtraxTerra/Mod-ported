// Adapted from chaevsfe / Create Fly: Diesel Generators (https://github.com/chaevsfe/Create-Diesel-Generators)
// Based on original work from Create: Diesel Generators by George VI (https://github.com/george8188625/Create-Diesel-Generators)
// Licensed under MIT — extracted/adapted with AI assistance.
package com.jesz.createdieselgenerators.client.behaviour;

import com.zurrtum.create.client.api.goggles.IHaveGoggleInformation;
import com.zurrtum.create.client.api.goggles.IHaveHoveringInformation;
import com.zurrtum.create.client.foundation.blockEntity.behaviour.tooltip.KineticTooltipBehaviour;
import com.zurrtum.create.content.kinetics.base.KineticBlockEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.minecraft.network.chat.Component;

import java.util.List;

public class CDGKineticGoggleBehaviour<T extends KineticBlockEntity> extends KineticTooltipBehaviour<T> {
    public CDGKineticGoggleBehaviour(T blockEntity) {
        super(blockEntity);
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        int before = tooltip.size();
        boolean added = super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        if (blockEntity instanceof IHaveGoggleInformation goggles)
            added |= goggles.addToGoggleTooltip(tooltip, isPlayerSneaking);
        return added && tooltip.size() > before;
    }

    @Override
    public boolean addToTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        int before = tooltip.size();
        boolean added = super.addToTooltip(tooltip, isPlayerSneaking);
        if (blockEntity instanceof IHaveHoveringInformation hovering)
            added |= hovering.addToTooltip(tooltip, isPlayerSneaking);
        return added && tooltip.size() > before;
    }
}
