// Adapted from chaevsfe / Create Fly: Diesel Generators (https://github.com/chaevsfe/Create-Diesel-Generators)
// Based on original work from Create: Diesel Generators by George VI (https://github.com/george8188625/Create-Diesel-Generators)
// Licensed under MIT — extracted/adapted with AI assistance.
package com.jesz.createdieselgenerators.content.molds;

import com.jesz.createdieselgenerators.CDGDataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

public class MoldItem extends Item {

    public MoldItem(Properties properties) {
        super(properties);
    }

    public static MoldType getMold(ItemStack stack) {
        if (!stack.has(CDGDataComponents.MOLD_TYPE))
            return null;
        return MoldType.findById(stack.get(CDGDataComponents.MOLD_TYPE));
    }

    @Override
    public Component getName(ItemStack stack) {
        MoldType type = getMold(stack);
        if(type == null)
            return super.getName(stack);
        return Component.translatable("mold."+type.id.getNamespace()+"."+type.id.getPath());
    }
}
