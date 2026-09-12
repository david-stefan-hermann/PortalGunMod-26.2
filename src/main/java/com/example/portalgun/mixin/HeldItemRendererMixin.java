package com.example.portalgun.mixin;

import com.example.portalgun.PortalGunMod;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemInHandRenderer.class)
public abstract class HeldItemRendererMixin {
   public HeldItemRendererMixin() {
   }

   @Shadow
   public abstract void renderItem(LivingEntity var1, ItemStack var2, ItemDisplayContext var3, PoseStack var4, SubmitNodeCollector var5, int var6);

   @Inject(method = "submitArmWithItem", at = @At("HEAD"), cancellable = true)
   private void portalgun$renderPortalGunWithoutVanillaHandMotion(
      AbstractClientPlayer player,
      float tickDelta,
      float pitch,
      InteractionHand hand,
      float swingProgress,
      ItemStack stack,
      float equipProgress,
      PoseStack matrices,
      SubmitNodeCollector renderQueue,
      int light,
      CallbackInfo ci
   ) {
      if (isPortalGun(stack)) {
         HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
         boolean rightHanded = arm == HumanoidArm.RIGHT;
         float side = rightHanded ? 1.0F : -1.0F;
         matrices.pushPose();
         matrices.translate(side * 0.56F, -0.52F, -0.72F);
         this.renderItem(player, stack, rightHanded ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND, matrices, renderQueue, light);
         matrices.popPose();
         ci.cancel();
      }
   }

   @Inject(method = "shouldInstantlyReplaceVisibleItem", at = @At("HEAD"), cancellable = true)
   private void portalgun$skipPortalGunFiringSwapAnimation(ItemStack oldStack, ItemStack newStack, CallbackInfoReturnable<Boolean> cir) {
      if (isPortalGun(oldStack) && isPortalGun(newStack)) {
         cir.setReturnValue(true);
      }
   }

   private static boolean isPortalGun(ItemStack stack) {
      return stack != null && stack.getItem() == PortalGunMod.PORTAL_GUN;
   }
}
