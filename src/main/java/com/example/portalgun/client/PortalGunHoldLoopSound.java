package com.example.portalgun.client;

import com.example.portalgun.ModSounds;
import com.example.portalgun.PortalGunMod;
import java.util.function.BooleanSupplier;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SoundInstance.Attenuation;

final class PortalGunHoldLoopSound extends AbstractTickableSoundInstance {
   private final Minecraft client;
   private final BooleanSupplier activeSupplier;

   PortalGunHoldLoopSound(Minecraft client, BooleanSupplier activeSupplier) {
      super(ModSounds.HOLD_LOOP, SoundSource.PLAYERS, RandomSource.create());
      this.client = client;
      this.activeSupplier = activeSupplier;
      this.looping = true;
      this.delay = 0;
      this.volume = 0.45F;
      this.pitch = 1.0F;
      this.attenuation = Attenuation.NONE;
      this.relative = true;
   }

   public void tick() {
      LocalPlayer player = this.client.player;
      if (player != null && this.activeSupplier.getAsBoolean() && player.getMainHandItem().getItem() == PortalGunMod.PORTAL_GUN) {
         Vec3 eyePos = player.getEyePosition();
         this.x = eyePos.x;
         this.y = eyePos.y;
         this.z = eyePos.z;
      } else {
         this.stopLoop();
      }
   }

   void stopLoop() {
      this.stop();
   }
}
