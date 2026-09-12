package com.example.portalgun.client;

import com.example.portalgun.block.PortalGunPedestalBlock;
import com.example.portalgun.block.entity.PortalGunPedestalBlockEntity;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.texture.OverlayTexture;
import com.mojang.math.Axis;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context;

public class PortalGunPedestalBlockEntityRenderer implements BlockEntityRenderer<PortalGunPedestalBlockEntity, PortalGunPedestalBlockEntityRenderState> {
   private final ItemModelResolver itemModelManager;

   public PortalGunPedestalBlockEntityRenderer(Context context) {
      this.itemModelManager = context.comp_4536();
   }

   public PortalGunPedestalBlockEntityRenderState createRenderState() {
      return new PortalGunPedestalBlockEntityRenderState();
   }

   public void updateRenderState(
      PortalGunPedestalBlockEntity pedestal,
      PortalGunPedestalBlockEntityRenderState state,
      float tickProgress,
      Vec3 cameraPos,
      CrumblingOverlay crumblingOverlay
   ) {
      super.extractRenderState(pedestal, state, tickProgress, cameraPos, crumblingOverlay);
      state.facing = (Direction)pedestal.getBlockState().getValue(PortalGunPedestalBlock.FACING);
      ItemStack stack = pedestal.getPortalGun();
      if (stack.isEmpty()) {
         state.portalGunRenderState = null;
      } else {
         ItemStackRenderState itemState = new ItemStackRenderState();
         int seed = (int)pedestal.getBlockPos().asLong();
         this.itemModelManager.updateForTopItem(itemState, stack, ItemDisplayContext.NONE, pedestal.level(), pedestal, seed);
         state.portalGunRenderState = itemState;
      }
   }

   public void render(PortalGunPedestalBlockEntityRenderState state, PoseStack matrices, SubmitNodeCollector queue, CameraRenderState cameraState) {
      if (state.portalGunRenderState != null) {
         matrices.pushPose();
         matrices.translate(0.5, 1.08, 0.5);
         matrices.mulPose(Axis.YP.rotationDegrees(-state.facing.toYRot() + 180.0F));
         matrices.translate(-0.15, 0.0, 0.0);
         matrices.mulPose(Axis.XP.rotationDegrees(-18.0F));
         matrices.scale(0.6F, 0.6F, 0.6F);
         state.portalGunRenderState.submit(matrices, queue, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
         matrices.popPose();
      }
   }
}
