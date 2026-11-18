// Adapted from chaevsfe / Create Fly: Diesel Generators (https://github.com/chaevsfe/Create-Diesel-Generators)
// Based on original work from Create: Diesel Generators by George VI (https://github.com/george8188625/Create-Diesel-Generators)
// Licensed under MIT — extracted/adapted with AI assistance.
package com.jesz.createdieselgenerators.client.behaviour;

import com.jesz.createdieselgenerators.CDGBlockEntityTypes;
import com.zurrtum.create.client.AllBlockEntityBehaviours;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

public final class CDGTooltipBehaviours {
    private CDGTooltipBehaviours() {
    }

    public static void register() {
        AllBlockEntityBehaviours.add(CDGBlockEntityTypes.BULK_FERMENTER.get(), CDGGoggleBehaviour::new);
        AllBlockEntityBehaviours.add(CDGBlockEntityTypes.BURNER.get(), CDGKineticGoggleBehaviour::new);
        AllBlockEntityBehaviours.add(CDGBlockEntityTypes.CANISTER.get(), CDGGoggleBehaviour::new);
        AllBlockEntityBehaviours.add(CDGBlockEntityTypes.CHEMICAL_TURRET.get(), CDGKineticGoggleBehaviour::new);
        AllBlockEntityBehaviours.add(CDGBlockEntityTypes.DIESEL_ENGINE.get(), CDGGeneratingKineticGoggleBehaviour::new);
        AllBlockEntityBehaviours.add(CDGBlockEntityTypes.DISTILLATION_TANK.get(), CDGGoggleBehaviour::new);
        AllBlockEntityBehaviours.add(CDGBlockEntityTypes.HUGE_DIESEL_ENGINE.get(), CDGGoggleBehaviour::new);
        AllBlockEntityBehaviours.add(CDGBlockEntityTypes.MODULAR_DIESEL_ENGINE.get(), CDGGeneratingKineticGoggleBehaviour::new);
        AllBlockEntityBehaviours.add(CDGBlockEntityTypes.OIL_BARREL.get(), CDGGoggleBehaviour::new);
        AllBlockEntityBehaviours.add(CDGBlockEntityTypes.PUMPJACK_HOLE.get(), CDGGoggleBehaviour::new);
        AllBlockEntityBehaviours.add(CDGBlockEntityTypes.POWERED_ENGINE_SHAFT.get(), CDGGeneratingKineticGoggleBehaviour::new);
        AllBlockEntityBehaviours.add(CDGBlockEntityTypes.PUMPJACK_CRANK.get(), CDGKineticGoggleBehaviour::new);
        AllBlockEntityBehaviours.add(CDGBlockEntityTypes.ENCASED_GIRDER.get(), CDGKineticGoggleBehaviour::new);
    }
}
