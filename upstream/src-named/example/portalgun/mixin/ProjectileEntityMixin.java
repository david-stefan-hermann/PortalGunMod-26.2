package com.example.portalgun.mixin;

import com.example.portalgun.portal.PortalManager;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Projectile.class)
public abstract class ProjectileEntityMixin {
   public ProjectileEntityMixin() {
   }

   @Inject(method = "hitTargetOrDeflectSelf", at = @At("HEAD"), cancellable = true)
   private void portalgun$teleportThroughPortalBeforeCollision(HitResult hit, CallbackInfoReturnable<ProjectileDeflection> cir) {
      Projectile projectile = (Projectile)this;
      if (PortalManager.teleportProjectileCollision(projectile, hit)) {
         cir.setReturnValue(ProjectileDeflection.NONE);
      }
   }
}
