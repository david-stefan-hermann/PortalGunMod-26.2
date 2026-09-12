package com.example.portalgun.util;

import net.minecraft.core.Direction;

/**
 * Replacement for {@code BlockAndTintGetter.getShade(Direction, true)}, which no longer exists in 26.2.
 * Values are the vanilla overworld face shades.
 */
public final class PortalShade {
   private PortalShade() {
   }

   public static float faceShade(Direction direction) {
      return switch (direction) {
         case DOWN -> 0.5F;
         case UP -> 1.0F;
         case NORTH, SOUTH -> 0.8F;
         case EAST, WEST -> 0.6F;
      };
   }
}
