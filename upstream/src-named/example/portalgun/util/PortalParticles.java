package com.example.portalgun.util;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import org.joml.Vector3f;

public final class PortalParticles {
   private PortalParticles() {
   }

   public static ParticleOptions createDust(Vector3f color, float scale) {
      Vector3f safeColor = color == null ? new Vector3f(1.0F, 1.0F, 1.0F) : color;
      return new DustParticleOptions(toRgb(safeColor), scale);
   }

   private static int toRgb(Vector3f color) {
      int r = clampColor(color.x);
      int g = clampColor(color.y);
      int b = clampColor(color.z);
      return r << 16 | g << 8 | b;
   }

   private static int clampColor(float value) {
      int i = Math.round(value * 255.0F);
      if (i < 0) {
         return 0;
      } else {
         return i > 255 ? 255 : i;
      }
   }
}
