package com.zurrtum.create.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.zurrtum.create.content.contraptions.glue.SuperGlueHandler;
import com.zurrtum.create.content.equipment.symmetryWand.SymmetryHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemStack.class)
public class ItemStackMixin {
    // Port note: upstream injects twice into vanilla's useOn -- before Item.useOn (to snapshot a
    // BlockPlaceContext while the target is still unchanged) and at wasItemInteraction() (to run
    // glue and symmetry after a successful placement). NeoForge rewrites useOn: on the server it
    // returns CommonHooks.onPlaceItemIntoWorld(context), which calls Item.useOn itself and fires the
    // block-place events. Neither upstream target is left in ItemStack. Wrapping that one call is the
    // same thing in one place: it only runs server-side, after NeoForge's adventure-mode check, and a
    // placement cancelled by an event comes back as FAIL -- not a Success, so nothing runs, as
    // upstream would not have run either.
    @WrapOperation(method = "useOn(Lnet/minecraft/world/item/context/UseOnContext;)Lnet/minecraft/world/InteractionResult;", at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/common/CommonHooks;onPlaceItemIntoWorld(Lnet/minecraft/world/item/context/UseOnContext;)Lnet/minecraft/world/InteractionResult;"))
    private InteractionResult placeItemIntoWorld(UseOnContext context, Operation<InteractionResult> original) {
        BlockPlaceContext placementContext = ((ItemStack) (Object) this).getItem() instanceof BlockItem ? new BlockPlaceContext(context) : null;
        InteractionResult result = original.call(context);
        Player player = context.getPlayer();
        if (placementContext != null && player != null && result instanceof InteractionResult.Success) {
            ServerLevel world = (ServerLevel) context.getLevel();
            BlockPos pos = placementContext.getClickedPos();
            SuperGlueHandler.glueListensForBlockPlacement(world, player, pos);
            if (!context.getItemInHand().components.has(DataComponents.CONSUMABLE)) {
                SymmetryHandler.onBlockPlaced(world, player, pos, placementContext);
            }
        }
        return result;
    }
}
