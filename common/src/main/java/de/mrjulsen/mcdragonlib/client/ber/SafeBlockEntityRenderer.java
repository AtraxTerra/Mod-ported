package de.mrjulsen.mcdragonlib.client.ber;

import org.jetbrains.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;

import de.mrjulsen.mcdragonlib.DragonLib;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/**
 * NeoForge 26.2 port: block entity renderers extract a render state, then submit it. DragonLib's
 * renderers draw from the block entity itself ({@link BERGraphics#blockEntity()}), so the state carries
 * the block entity, and {@link #submit} runs {@link #renderSafe} with a {@link BERGraphics} whose
 * buffers go to the frame's collector. Both steps happen on the render thread in the same frame, as the
 * single 1.21 render call did.
 */
public abstract class SafeBlockEntityRenderer<T extends BlockEntity> implements BlockEntityRenderer<T, SafeBlockEntityRenderer.State<T>> {

	private final BlockEntityRendererProvider.Context context;
	private boolean failureLogged = false;

	public SafeBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
		this.context = context;
	}

	/** The block entity and frame values a DragonLib renderer needs at submit time. */
	public static class State<T extends BlockEntity> extends BlockEntityRenderState {
		public @Nullable T blockEntity;
		public float partialTicks;
	}

	@Override
	public State<T> createRenderState() {
		return new State<>();
	}

	@Override
	public void extractRenderState(T be, State<T> state, float partialTicks, Vec3 cameraPos, @Nullable ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
		BlockEntityRenderState.extractBase(be, state, crumblingOverlay);
		state.blockEntity = isInvalid(be) ? null : be;
		state.partialTicks = partialTicks;
	}

	@Override
	public final void submit(State<T> state, PoseStack ms, SubmitNodeCollector collector, CameraRenderState camera) {
		T be = state.blockEntity;
		state.blockEntity = null;
		if (be == null) {
			return;
		}
		BERGraphics<T> graphics = new BERGraphics<>(be, ms, collector, context, state.lightCoords, OverlayTexture.NO_OVERLAY, state.partialTicks);
		try {
			renderSafe(graphics, state.partialTicks);
		} catch (RuntimeException e) {
			// A 26.2 renderer that throws takes the whole frame down; skip this block entity instead.
			if (!failureLogged) {
				failureLogged = true;
				DragonLib.LOGGER.error("Block entity renderer {} failed; it is skipped from now on.", getClass().getName(), e);
			}
			return;
		}
		graphics.flush();
	}

	protected abstract void renderSafe(BERGraphics<T> graphics, float partialTicks);

	public boolean isInvalid(T be) {
		return !be.hasLevel() || be.getBlockState().getBlock() == Blocks.AIR;
	}
}
