package com.example.portalgun.client;

import com.example.portalgun.item.PortalGunItem;
import com.mojang.serialization.MapCodec;
import java.util.function.Consumer;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.item.ItemStack;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.client.renderer.special.SpecialModelRenderer.Unbaked;
import net.minecraft.client.renderer.special.SpecialModelRenderer.BakingContext;
import org.joml.Vector3fc;

public final class PortalGunItemRenderer implements SpecialModelRenderer<PortalGunItemRenderer.RenderData> {
   private static final float SHOT_KICK_X = -0.07F;
   private static final float SHOT_KICK_Y = 0.025F;
   private static final float SHOT_YAW_DEGREES = -3.5F;
   private static final float SHOT_ROLL_DEGREES = 2.2F;
   private static final float CLEAR_SWAY_PIVOT_X = 0.62F;
   private static final float CLEAR_SWAY_PIVOT_Y = 0.5F;
   private static final float CLEAR_SWAY_PIVOT_Z = 0.55F;
   private static final float CLEAR_SWAY_YAW_DEGREES = 6.0F;
   private static final float CLEAR_SWAY_ROLL_DEGREES = 8.0F;

   public PortalGunItemRenderer() {
   }

   public void render(
      PortalGunItemRenderer.RenderData data,
      ItemDisplayContext displayContext,
      PoseStack matrices,
      SubmitNodeCollector renderQueue,
      int light,
      int overlay,
      boolean glint,
      int renderLayer
   ) {
      int firingTicks = data == null ? 0 : data.firingTicks();
      int clearTicks = data == null ? 0 : data.clearTicks();
      boolean grabActive = data != null && data.grabActive();
      matrices.pushPose();
      applyFirstPersonShotKick(displayContext, matrices, firingTicks);
      applyFirstPersonClearShake(displayContext, matrices, clearTicks);
      PortalGunObjModel.current().render(matrices, renderQueue, light, overlay, firingTicks, grabActive);
      matrices.popPose();
   }

   public void getExtents(Consumer<Vector3fc> consumer) {
      PortalGunObjModel.current().collectVertices(consumer);
   }

   public PortalGunItemRenderer.RenderData getData(ItemStack stack) {
      return new PortalGunItemRenderer.RenderData(PortalGunItem.getAnimTicks(stack), PortalGunItem.getClearAnimTicks(stack), PortalGunItem.isGrabActive(stack));
   }

   private static void applyFirstPersonShotKick(ItemDisplayContext displayContext, PoseStack matrices, int firingTicks) {
      if (firingTicks > 0 && displayContext.firstPerson()) {
         float side = displayContext.leftHand() ? -1.0F : 1.0F;
         float kick = easeOut(firingTicks / 12.0F);
         matrices.translate(-0.07F * kick, 0.025F * kick, 0.0F);
         matrices.mulPose(Axis.YP.rotationDegrees(side * -3.5F * kick));
         matrices.mulPose(Axis.ZP.rotationDegrees(side * 2.2F * kick));
      }
   }

   private static void applyFirstPersonClearShake(ItemDisplayContext displayContext, PoseStack matrices, int clearTicks) {
      if (clearTicks > 0 && displayContext.firstPerson()) {
         float handSide = displayContext.leftHand() ? -1.0F : 1.0F;
         float time = clearTicks / 14.0F;
         float elapsed = 1.0F - time;
         float fade = time * time;
         float sway = (float)Math.sin(elapsed * Math.PI * 6.0) * fade;
         float settle = (float)Math.sin(elapsed * Math.PI) * fade;
         matrices.translate(0.0F, 0.004F * settle, 0.0F);
         matrices.translate(0.62F, 0.5F, 0.55F);
         matrices.mulPose(Axis.YP.rotationDegrees(handSide * 6.0F * sway));
         matrices.mulPose(Axis.ZP.rotationDegrees(handSide * 8.0F * sway));
         matrices.translate(-0.62F, -0.5F, -0.55F);
      }
   }

   private static float easeOut(float value) {
      float clamped = Math.max(0.0F, Math.min(1.0F, value));
      return 1.0F - (1.0F - clamped) * (1.0F - clamped);
   }

   public record RenderData(int firingTicks, int clearTicks, boolean grabActive) {
      public RenderData {
      }
   }

   public record Unbaked() implements Unbaked {
      public static final PortalGunItemRenderer.Unbaked INSTANCE = new PortalGunItemRenderer.Unbaked();
      public static final MapCodec<PortalGunItemRenderer.Unbaked> CODEC = MapCodec.unit(INSTANCE);

      public SpecialModelRenderer<?> bake(BakingContext context) {
         return new PortalGunItemRenderer();
      }

      public MapCodec<PortalGunItemRenderer.Unbaked> type() {
         return CODEC;
      }
   }
}
