// Adapted from chaevsfe / Create Fly: Diesel Generators (https://github.com/chaevsfe/Create-Diesel-Generators)
// Based on original work from Create: Diesel Generators by George VI (https://github.com/george8188625/Create-Diesel-Generators)
// Licensed under MIT — extracted/adapted with AI assistance.
package com.jesz.createdieselgenerators.client.model;

import com.jesz.createdieselgenerators.CDGDataComponents;
import com.jesz.createdieselgenerators.CreateDieselGenerators;
import com.jesz.createdieselgenerators.content.track_layers_bag.TrackLayersBagItem;
import com.mojang.serialization.MapCodec;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterRangeSelectItemModelPropertyEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

@EventBusSubscriber(modid = CreateDieselGenerators.ID, value = Dist.CLIENT)
public final class CDGItemProperties {
    public static final Identifier OIL_SCANNER_STATE = CreateDieselGenerators.rl("oil_scanner_state");
    public static final Identifier TRACKS = CreateDieselGenerators.rl("tracks");

    private CDGItemProperties() {
    }

    public static void register() {
        // Port: Fabric API widens RangeSelectItemModelProperties.ID_MAPPER; NeoForge registers through
        // RegisterRangeSelectItemModelPropertyEvent instead, below.
    }

    @SubscribeEvent
    public static void onRegisterRangeSelectProperties(RegisterRangeSelectItemModelPropertyEvent event) {
        event.register(OIL_SCANNER_STATE, OilScannerState.MAP_CODEC);
        event.register(TRACKS, Tracks.MAP_CODEC);
    }

    public record OilScannerState() implements RangeSelectItemModelProperty {
        public static final MapCodec<OilScannerState> MAP_CODEC = MapCodec.unit(new OilScannerState());

        @Override
        public float get(ItemStack stack, @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
            return stack.getOrDefault(CDGDataComponents.OIL_SCANNER_STATE, 0);
        }

        @Override
        public MapCodec<OilScannerState> type() {
            return MAP_CODEC;
        }
    }

    public record Tracks() implements RangeSelectItemModelProperty {
        public static final MapCodec<Tracks> MAP_CODEC = MapCodec.unit(new Tracks());

        @Override
        public float get(ItemStack stack, @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
            return TrackLayersBagItem.getTracks(stack).getCount();
        }

        @Override
        public MapCodec<Tracks> type() {
            return MAP_CODEC;
        }
    }
}
