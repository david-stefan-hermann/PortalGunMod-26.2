package com.example.portalgun;

import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.core.registries.BuiltInRegistries;

public class ModSounds {
   public static SoundEvent PORTAL_SHOOT_BLUE;
   public static SoundEvent PORTAL_SHOOT_ORANGE;
   public static SoundEvent PORTAL_GUN_ACTIVATE;
   public static SoundEvent PORTAL_TELEPORT;
   public static SoundEvent PORTAL_FAIL;
   public static SoundEvent PORTAL_FIZZLE;
   public static SoundEvent HOLD_LOOP;
   public static SoundEvent PHYSCANNON_DROP;
   public static SoundEvent PHYSCANNON_TOOHEAVY;

   public ModSounds() {
   }

   public static void init() {
      if (PORTAL_SHOOT_BLUE == null) {
         PORTAL_SHOOT_BLUE = register("portal_shoot_blue");
      }

      if (PORTAL_SHOOT_ORANGE == null) {
         PORTAL_SHOOT_ORANGE = register("portal_shoot_orange");
      }

      if (PORTAL_GUN_ACTIVATE == null) {
         PORTAL_GUN_ACTIVATE = register("portal_gun_activate");
      }

      if (PORTAL_TELEPORT == null) {
         PORTAL_TELEPORT = register("teleport");
      }

      if (PORTAL_FAIL == null) {
         PORTAL_FAIL = register("portal_fail");
      }

      if (PORTAL_FIZZLE == null) {
         PORTAL_FIZZLE = register("portal_fizzle");
      }

      if (HOLD_LOOP == null) {
         HOLD_LOOP = register("hold_loop");
      }

      if (PHYSCANNON_DROP == null) {
         PHYSCANNON_DROP = register("physcannon_drop");
      }

      if (PHYSCANNON_TOOHEAVY == null) {
         PHYSCANNON_TOOHEAVY = register("physcannon_tooheavy");
      }
   }

   private static SoundEvent register(String name) {
      Identifier id = Identifier.fromNamespaceAndPath("portalgun", name);
      return (SoundEvent)Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
   }
}
