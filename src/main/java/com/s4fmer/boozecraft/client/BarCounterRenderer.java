package com.s4fmer.boozecraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.s4fmer.boozecraft.block.BarCounterBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Draws up to three glasses standing on the counter top. */
public class BarCounterRenderer implements BlockEntityRenderer<BarCounterBlockEntity> {

	private final ItemRenderer itemRenderer;

	public BarCounterRenderer(BlockEntityRendererProvider.Context context) {
		this.itemRenderer = context.getItemRenderer();
	}

	@Override
	public void render(BarCounterBlockEntity counter, float partialTick, PoseStack pose, MultiBufferSource buffers,
			int packedLight, int packedOverlay) {
		for (int i = 0; i < BarCounterBlockEntity.SLOTS; i++) {
			ItemStack stack = counter.getItem(i);
			if (stack.isEmpty()) {
				continue;
			}
			pose.pushPose();
			pose.translate(0.25D + i * 0.25D, 1.01D, 0.5D);
			pose.scale(0.45F, 0.45F, 0.45F);
			pose.mulPose(Axis.XP.rotationDegrees(90.0F));
			this.itemRenderer.renderStatic(stack, ItemDisplayContext.FIXED, packedLight, packedOverlay, pose, buffers,
					counter.getLevel(), 0);
			pose.popPose();
		}
	}
}
