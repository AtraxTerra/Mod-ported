/*
 * Steam 'n' Rails
 * Copyright (c) 2022-2024 The Railways Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.railwayteam.railways.content.conductor.fabric;

import com.mojang.blaze3d.vertex.PoseStack;
import com.railwayteam.railways.content.conductor.ConductorCapItem;
import com.railwayteam.railways.content.conductor.ConductorCapLayer;
import com.railwayteam.railways.content.conductor.ConductorCapModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.item.ItemStack;

/**
 * Port: the conductor cap worn by a player. Upstream draws it with Fabric API's {@code ArmorRenderer}
 * in the armour layer; NeoForge has no such hook for an armour item without an equipment asset, so
 * the same drawing is a render layer that RailwaysNeoForgeClientEvents adds to the player (and
 * mannequin) renderers. The cap has no equipment asset, so neither the armour layer nor the head item
 * layer draws anything else for it, exactly as with upstream's {@code shouldRenderDefaultHeadItem}.
 */
public class ConductorCapItemRenderer<S extends HumanoidRenderState, M extends HumanoidModel<S>> extends RenderLayer<S, M> {
	private final ConductorCapModel<?> model;

	public ConductorCapItemRenderer(RenderLayerParent<S, M> parent, EntityModelSet models) {
		super(parent);
		model = new ConductorCapModel<>(models.bakeLayer(ConductorCapModel.LAYER_LOCATION));
	}

	/** Upstream registers its armour renderer here; the layers are added when renderers are built. */
	public static void register() {
	}

	@Override
	@SuppressWarnings({"rawtypes", "unchecked"})
	public void submit(PoseStack matrices, SubmitNodeCollector submitter, int light, S state, float yRot, float xRot) {
		ItemStack stack = state.headEquipment;
		if (!(stack.getItem() instanceof ConductorCapItem cap))
			return;

		matrices.pushPose();
		getParentModel().getHead().translateAndRotate(matrices);
		// Match the conductor entity cap: render in head-local space so pitch, yaw, and roll all follow the head.
		matrices.translate(0.0F, 0.0F, -0.25F / 16.0F);
		submitter.submitModel((Model) model, state, matrices, ConductorCapLayer.entityCutoutNoCull(cap.textureId),
			light, LivingEntityRenderer.getOverlayCoords(state, 0), -1, null);
		matrices.popPose();
	}
}
