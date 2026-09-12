package com.example.portalgun.mixin;

import com.example.portalgun.PortalGunMod;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public class ClientPlayerInteractionManagerMixin {
   @Shadow
   @Final
   private Minecraft minecraft;

   public ClientPlayerInteractionManagerMixin() {
   }

   @Inject(method = "startDestroyBlock", at = @At("HEAD"), cancellable = true)
   private void portalgun$cancelAttack(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
      if (this.minecraft.player != null) {
         ItemStack stack = this.minecraft.player.getMainHandItem();
         if (stack.getItem() == PortalGunMod.PORTAL_GUN) {
            cir.setReturnValue(false);
            cir.cancel();
         }
      }
   }
}
