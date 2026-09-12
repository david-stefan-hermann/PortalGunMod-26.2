package com.example.portalgun.mixin;

import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractArrow.class)
public interface PersistentProjectileEntityAccessor {
   @Invoker("setInGround")
   void portalgun$setInGround(boolean var1);

   @Accessor("inGroundTime")
   void portalgun$setInGroundTime(int var1);
}
