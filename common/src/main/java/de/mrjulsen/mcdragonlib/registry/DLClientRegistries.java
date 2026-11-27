package de.mrjulsen.mcdragonlib.registry;

import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

public class DLClientRegistries {
    
    // NeoForge-only build: the call Architectury's @ExpectPlatform transformer generated.
    public static <T extends BlockEntity> void register(BlockEntityType<T> type, BlockEntityRendererProvider<? super T> provider) {
        de.mrjulsen.mcdragonlib.registry.neoforge.DLClientRegistriesImpl.register(type, provider);
    }
}
