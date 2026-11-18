// Adapted from chaevsfe / Create Fly: Diesel Generators (https://github.com/chaevsfe/Create-Diesel-Generators)
// Based on original work from Create: Diesel Generators by George VI (https://github.com/george8188625/Create-Diesel-Generators)
// Licensed under MIT — extracted/adapted with AI assistance.
package com.jesz.createdieselgenerators.client.behaviour;

import com.zurrtum.create.client.api.goggles.IHaveGoggleInformation;
import com.zurrtum.create.client.api.goggles.IHaveHoveringInformation;
import com.zurrtum.create.client.foundation.blockEntity.behaviour.tooltip.TooltipBehaviour;
import com.zurrtum.create.foundation.blockEntity.SmartBlockEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.minecraft.network.chat.Component;

import java.util.List;

public class CDGGoggleBehaviour extends TooltipBehaviour<SmartBlockEntity> implements IHaveGoggleInformation, IHaveHoveringInformation {
    public CDGGoggleBehaviour(SmartBlockEntity blockEntity) {
        super(blockEntity);
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        if (!(blockEntity instanceof IHaveGoggleInformation goggles))
            return false;
        int before = tooltip.size();
        return goggles.addToGoggleTooltip(tooltip, isPlayerSneaking) && tooltip.size() > before;
    }

    @Override
    public boolean addToTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        if (!(blockEntity instanceof IHaveHoveringInformation hovering))
            return false;
        int before = tooltip.size();
        return hovering.addToTooltip(tooltip, isPlayerSneaking) && tooltip.size() > before;
    }
}
