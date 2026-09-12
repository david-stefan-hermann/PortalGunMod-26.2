package com.example.portalgun;

import com.example.portalgun.portal.PortalEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType.Builder;

public class ModEntities {
   public static EntityType<PortalEntity> PORTAL;

   public ModEntities() {
   }

   public static void init() {
      Identifier id = Identifier.fromNamespaceAndPath("portalgun", "portal");
      ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, id);
      PORTAL = (EntityType<PortalEntity>)Registry.register(
         BuiltInRegistries.ENTITY_TYPE,
         id,
         Builder.of(PortalEntity::new, MobCategory.MISC)
            .sized(1.0F, 2.0F)
            .clientTrackingRange(64)
            .updateInterval(1)
            .noSave()
            .noSummon()
            .build(key)
      );
   }
}
