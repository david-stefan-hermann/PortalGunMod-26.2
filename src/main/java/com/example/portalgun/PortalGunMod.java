package com.example.portalgun;

import com.example.portalgun.block.PortalGunPedestalBlock;
import com.example.portalgun.block.entity.PortalGunPedestalBlockEntity;
import com.example.portalgun.item.PortalGunItem;
import com.example.portalgun.network.ClearPortalsPayload;
import com.example.portalgun.network.GrabStatusPayload;
import com.example.portalgun.network.PortalStatusPayload;
import com.example.portalgun.network.PortalViewPayload;
import com.example.portalgun.network.ShootBluePayload;
import com.example.portalgun.network.ThrowGrabPayload;
import com.example.portalgun.network.ToggleGrabPayload;
import com.example.portalgun.portal.PortalEntity;
import com.example.portalgun.portal.PortalManager;
import com.example.portalgun.portal.PortalType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStarted;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStopping;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.EndTick;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.Before;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents.ModifyEntries;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.Join;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.world.item.SwingAnimationType;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.permissions.Permission.HasCommandLevel;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PortalGunMod implements ModInitializer {
   public static final String MODID = "portalgun";
   public static final Logger LOGGER = LoggerFactory.getLogger("portalgun");
   private static final String GRABBED_TAG = "portalgun_grabbed";
   private static final double GRAB_RANGE = 8.0;
   private static final double GRAB_HOLD_DISTANCE = 3.0;
   private static final double GRAB_MIN_HOLD_DISTANCE = 1.0;
   private static final double GRAB_COLLISION_STEP = 0.1;
   private static final double GRAB_THROW_SPEED = 1.8;
   private static final double GRAB_THROW_UPWARD_BOOST = 0.15;
   private static final Permission CLEAR_ALL_PERMISSION = new HasCommandLevel(PermissionLevel.GAMEMASTERS);
   public static EntityType<PortalEntity> PORTAL_ENTITY;
   public static Item PORTAL_GUN;
   public static Block PORTAL_GUN_PEDESTAL;
   public static BlockEntityType<PortalGunPedestalBlockEntity> PORTAL_GUN_PEDESTAL_BLOCK_ENTITY;
   private static final Map<UUID, PortalGunMod.GrabState> GRABBED = new HashMap<>();

   public PortalGunMod() {
   }

   public void onInitialize() {
      ModSounds.init();
      ModEntities.init();
      PORTAL_ENTITY = ModEntities.PORTAL;
      Identifier pedestalId = Identifier.fromNamespaceAndPath("portalgun", "portal_gun_pedestal");
      PORTAL_GUN_PEDESTAL = (Block)Registry.register(
         BuiltInRegistries.BLOCK,
         pedestalId,
         new PortalGunPedestalBlock(
            createBlockSettings(pedestalId).mapColor(MapColor.WOOL).strength(1.8F, 6.0F).sound(SoundType.METAL).noOcclusion()
         )
      );
      PORTAL_GUN_PEDESTAL_BLOCK_ENTITY = (BlockEntityType<PortalGunPedestalBlockEntity>)Registry.register(
         BuiltInRegistries.BLOCK_ENTITY_TYPE,
         pedestalId,
         FabricBlockEntityTypeBuilder.create(PortalGunPedestalBlockEntity::new, new Block[]{PORTAL_GUN_PEDESTAL}).build()
      );
      Identifier portalGunId = Identifier.fromNamespaceAndPath("portalgun", "portal_gun");
      PORTAL_GUN = (Item)Registry.register(
         BuiltInRegistries.ITEM,
         portalGunId,
         new PortalGunItem(createItemSettings(portalGunId).stacksTo(1).component(DataComponents.SWING_ANIMATION, new SwingAnimation(SwingAnimationType.NONE, 6)))
      );
      Registry.register(BuiltInRegistries.ITEM, pedestalId, new BlockItem(PORTAL_GUN_PEDESTAL, createItemSettings(pedestalId).useBlockDescriptionPrefix()));
      ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register((ModifyEntries)entries -> entries.accept(PORTAL_GUN));
      ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register((ModifyEntries)entries -> entries.accept(PORTAL_GUN_PEDESTAL));
      PlayerBlockBreakEvents.BEFORE.register((Before)(world, player, pos, state, blockEntity) -> {
         ItemStack stack = player.getMainHandItem();
         return !(stack.getItem() instanceof PortalGunItem);
      });
      PayloadTypeRegistry.playC2S().register(ClearPortalsPayload.ID, ClearPortalsPayload.CODEC);
      PayloadTypeRegistry.playC2S().register(ShootBluePayload.ID, ShootBluePayload.CODEC);
      PayloadTypeRegistry.playC2S().register(ToggleGrabPayload.ID, ToggleGrabPayload.CODEC);
      PayloadTypeRegistry.playC2S().register(ThrowGrabPayload.ID, ThrowGrabPayload.CODEC);
      PayloadTypeRegistry.playS2C().register(PortalStatusPayload.ID, PortalStatusPayload.CODEC);
      PayloadTypeRegistry.playS2C().register(GrabStatusPayload.ID, GrabStatusPayload.CODEC);
      PayloadTypeRegistry.playS2C().register(PortalViewPayload.ID, PortalViewPayload.CODEC);
      ServerPlayNetworking.registerGlobalReceiver(ClearPortalsPayload.ID, (payload, context) -> context.server().execute(() -> {
         ServerPlayer player = context.player();
         if (hasPortalGunEquipped(player)) {
            PortalManager.clearForPlayer(context.server(), player.getUUID());
            PortalGunItem.markClearAnimation(player.getMainHandItem());
            PortalGunItem.markClearAnimation(player.getOffhandItem());
            player.displayClientMessage(Component.literal("Cleared your portals."), true);
         }
      }));
      ServerPlayNetworking.registerGlobalReceiver(ShootBluePayload.ID, (payload, context) -> context.server().execute(() -> {
         if (context.player() instanceof ServerPlayer serverPlayer) {
            if (!hasPortalGunInMainHand(serverPlayer)) {
               return;
            }

            if (isGrabbing(serverPlayer)) {
               return;
            }

            boolean fired = PortalGunItem.tryShootPortal(serverPlayer, PortalType.BLUE);
            if (fired) {
               PortalGunItem.markFiring(serverPlayer, serverPlayer.getMainHandItem());
            }
         }
      }));
      ServerPlayNetworking.registerGlobalReceiver(ToggleGrabPayload.ID, (payload, context) -> context.server().execute(() -> {
         if (context.player() instanceof ServerPlayer serverPlayer) {
            toggleGrab(serverPlayer);
         }
      }));
      ServerPlayNetworking.registerGlobalReceiver(ThrowGrabPayload.ID, (payload, context) -> context.server().execute(() -> {
         if (context.player() instanceof ServerPlayer serverPlayer) {
            throwGrab(serverPlayer);
         }
      }));
      ServerPlayConnectionEvents.JOIN.register((Join)(handler, sender, server) -> {
         PortalManager.sendStatusForPlayer(handler.player);
         setGrabVisualState(handler.player, false);
      });
      CommandRegistrationCallback.EVENT
         .register(
            (CommandRegistrationCallback)(dispatcher, registryAccess, environment) -> dispatcher.register(
               (LiteralArgumentBuilder)Commands.literal("portalgun")
                  .then(
                     ((LiteralArgumentBuilder)Commands.literal("clear").executes(ctx -> {
                           ServerPlayer player = ((CommandSourceStack)ctx.getSource()).getPlayerOrException();
                           PortalManager.clearForPlayer(((CommandSourceStack)ctx.getSource()).getServer(), player.getUUID());
                           ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> Component.literal("Cleared your portals."), false);
                           return 1;
                        }))
                        .then(
                           ((LiteralArgumentBuilder)Commands.literal("all").requires(source -> source.permissions().hasPermission(CLEAR_ALL_PERMISSION)))
                              .executes(ctx -> {
                                 PortalManager.clearAll(((CommandSourceStack)ctx.getSource()).getServer());
                                 ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> Component.literal("Cleared all portals."), true);
                                 return 1;
                              })
                        )
                  )
            )
         );
      ServerLifecycleEvents.SERVER_STARTED.register((ServerStarted)server -> {
         repairTaggedGrabs(server);
         GRABBED.clear();
         PortalManager.clearAll(server);
      });
      ServerLifecycleEvents.SERVER_STOPPING.register((ServerStopping)server -> {
         releaseAllGrabs(server);
         PortalManager.clearAll(server);
      });
      ServerTickEvents.END_SERVER_TICK.register((EndTick)server -> {
         if (!GRABBED.isEmpty()) {
            GRABBED.entrySet().removeIf(entry -> !updateGrab(server, entry.getKey(), entry.getValue()));
         }

         PortalManager.syncCrossDimensionViews(server);
      });
      LOGGER.info("Loaded");
   }

   private static Properties createItemSettings(Identifier id) {
      ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
      return new Properties().setId(key);
   }

   private static Properties createBlockSettings(Identifier id) {
      ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id);
      return Properties.of().setId(key);
   }

   private static boolean hasPortalGunInMainHand(Player player) {
      return player != null && isPortalGun(player.getMainHandItem());
   }

   private static boolean hasPortalGunEquipped(Player player) {
      return player != null && (isPortalGun(player.getMainHandItem()) || isPortalGun(player.getOffhandItem()));
   }

   private static boolean isPortalGun(ItemStack stack) {
      return stack != null && stack.getItem() == PORTAL_GUN;
   }

   public static boolean isGrabbing(Player player) {
      return player != null && GRABBED.containsKey(player.getUUID());
   }

   private static void toggleGrab(ServerPlayer player) {
      if (!hasPortalGunInMainHand(player)) {
         setGrabVisualState(player, false);
      } else {
         PortalGunMod.GrabState existing = GRABBED.remove(player.getUUID());
         if (existing != null) {
            releaseGrab(player.level().getServer(), existing);
            setGrabVisualState(player, false);
         } else {
            LivingEntity target = findGrabTarget(player);
            if (target == null) {
               FallingBlockEntity blockTarget = findBlockGrabTarget(player);
               if (blockTarget == null) {
                  if (hasFailedGrabTarget(player)) {
                     playTooHeavySound(player);
                  }

                  setGrabVisualState(player, false);
               } else {
                  startGrab(player, blockTarget);
               }
            } else {
               startGrab(player, target);
            }
         }
      }
   }

   private static void startGrab(ServerPlayer player, Entity target) {
      target.setNoGravity(true);
      target.setDeltaMovement(Vec3.ZERO);
      target.stopRiding();
      target.addTag("portalgun_grabbed");
      keepHeldEntityStable(target);
      GRABBED.put(player.getUUID(), new PortalGunMod.GrabState(target.getUUID()));
      setGrabVisualState(player, true);
   }

   private static boolean updateGrab(MinecraftServer server, UUID playerId, PortalGunMod.GrabState state) {
      ServerPlayer player = server.getPlayerList().getPlayer(playerId);
      if (player != null && player.getMainHandItem().getItem() == PORTAL_GUN) {
         Entity entity = findEntityByUuid(server, state.entityUuid);
         if (entity == null || !entity.isAlive()) {
            releaseGrab(server, state);
            setGrabVisualState(player, false);
            return false;
         }

         if (entity.level() != player.level()) {
            releaseGrab(server, state);
            setGrabVisualState(player, false);
            return false;
         }

         Vec3 forward = player.getViewVector(1.0F);
         Optional<Vec3> safePosition = findSafeGrabPosition(player, entity, forward);
         if (safePosition.isEmpty()) {
            if (isSafeGrabPosition(entity, entity.getX(), entity.getY(), entity.getZ())) {
               entity.setNoGravity(true);
               entity.setDeltaMovement(Vec3.ZERO);
               keepHeldEntityStable(entity);
               return true;
            } else {
               releaseGrab(server, state);
               setGrabVisualState(player, false);
               return false;
            }
         } else {
            entity.setNoGravity(true);
            Vec3 target = safePosition.get();
            entity.snapTo(target.x, target.y, target.z, entity.getYRot(), entity.getXRot());
            entity.setDeltaMovement(Vec3.ZERO);
            entity.needsSync = true;
            keepHeldEntityStable(entity);
            if (player.distanceTo(entity) > 10.0F) {
               releaseGrab(server, state);
               setGrabVisualState(player, false);
               return false;
            } else {
               return true;
            }
         }
      } else {
         releaseGrab(server, state);
         if (player != null) {
            setGrabVisualState(player, false);
         }

         return false;
      }
   }

   private static Optional<Vec3> findSafeGrabPosition(ServerPlayer player, Entity held, Vec3 forward) {
      Vec3 eye = player.getEyePosition();
      double halfHeight = held.getBbHeight() * 0.5;

      for (double distance = 3.0; distance >= 1.0; distance -= 0.1) {
         Vec3 center = eye.add(forward.scale(distance));
         double feetY = center.y - halfHeight;
         if (isSafeGrabPosition(held, center.x, feetY, center.z)) {
            return Optional.of(new Vec3(center.x, feetY, center.z));
         }
      }

      return Optional.empty();
   }

   private static boolean isSafeGrabPosition(Entity held, double feetX, double feetY, double feetZ) {
      double halfWidth = Math.max(0.05, held.getBbWidth() * 0.5);
      double height = Math.max(0.05, held.getBbHeight());
      AABB box = new AABB(feetX - halfWidth, feetY, feetZ - halfWidth, feetX + halfWidth, feetY + height, feetZ + halfWidth).deflate(0.001);
      return held.level().noCollision(held, box);
   }

   private static void releaseGrab(MinecraftServer server, PortalGunMod.GrabState state) {
      if (server != null) {
         Entity entity = findEntityByUuid(server, state.entityUuid);
         if (entity != null) {
            releaseHeldEntity(entity, true);
         }
      }
   }

   private static void releaseAllGrabs(MinecraftServer server) {
      for (PortalGunMod.GrabState state : GRABBED.values()) {
         releaseGrab(server, state);
      }

      GRABBED.clear();
   }

   private static void repairTaggedGrabs(MinecraftServer server) {
      if (server != null) {
         for (ServerLevel world : server.getAllLevels()) {
            for (Entity entity : world.getAllEntities()) {
               if (entity.getTags().contains("portalgun_grabbed")) {
                  releaseHeldEntity(entity, false);
               }
            }
         }
      }
   }

   private static FallingBlockEntity findBlockGrabTarget(ServerPlayer player) {
      HitResult hit = player.pick(8.0, 0.0F, false);
      if (hit.getType() == Type.BLOCK && hit instanceof BlockHitResult blockHit) {
         ServerLevel world = player.level();
         BlockPos pos = blockHit.getBlockPos();
         BlockState state = world.getBlockState(pos);
         if (!isGrabbableBlock(world, pos, state)) {
            return null;
         }

         CompoundTag blockEntityData = createGrabbedBlockEntityData(world, pos, state);
         FallingBlockEntity fallingBlock = FallingBlockEntity.fall(world, pos, state);
         fallingBlock.blockData = blockEntityData;
         fallingBlock.dropItem = true;
         fallingBlock.time = 1;
         return fallingBlock;
      } else {
         return null;
      }
   }

   private static boolean isGrabbableBlock(ServerLevel world, BlockPos pos, BlockState state) {
      if (state.isAir()) {
         return false;
      }

      if (!state.is(Blocks.NETHER_PORTAL)
         && !state.is(Blocks.END_PORTAL)
         && !state.is(Blocks.END_GATEWAY)
         && !state.is(Blocks.END_PORTAL_FRAME)) {
         if (state.getDestroySpeed(world, pos) < 0.0F) {
            return false;
         } else if (hasPortalOnBlock(world, pos)) {
            return false;
         } else if (state.is(Blocks.SPAWNER)) {
            return true;
         } else {
            return state.hasBlockEntity() ? false : state.isCollisionShapeFullBlock(world, pos) && state.canOcclude();
         }
      } else {
         return false;
      }
   }

   private static CompoundTag createGrabbedBlockEntityData(ServerLevel world, BlockPos pos, BlockState state) {
      if (!state.is(Blocks.SPAWNER)) {
         return null;
      }

      BlockEntity blockEntity = world.getBlockEntity(pos);
      if (blockEntity == null) {
         return null;
      }

      CompoundTag nbt = blockEntity.saveWithoutMetadata(world.registryAccess());
      return nbt.isEmpty() ? null : nbt;
   }

   private static boolean hasFailedGrabTarget(ServerPlayer player) {
      HitResult blockHit = player.pick(8.0, 0.0F, false);
      Vec3 start = player.getEyePosition(1.0F);
      Vec3 direction = player.getViewVector(1.0F);
      Vec3 end = start.add(direction.scale(8.0));
      double maxEntityDistance = 64.0;
      if (blockHit.getType() == Type.BLOCK) {
         maxEntityDistance = start.distanceToSqr(blockHit.getLocation());
      }

      AABB box = player.getBoundingBox().expandTowards(direction.scale(8.0)).inflate(1.0);
      EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(player, start, end, box, entity -> entity != player && entity.isPickable(), maxEntityDistance);
      if (entityHit != null) {
         return true;
      } else {
         return blockHit.getType() == Type.BLOCK && blockHit instanceof BlockHitResult blockHitResult
            ? !player.level().getBlockState(blockHitResult.getBlockPos()).isAir()
            : false;
      }
   }

   private static void playTooHeavySound(ServerPlayer player) {
      player.level()
         .playSound(
            null,
            player.getX(),
            player.getY(),
            player.getZ(),
            BuiltInRegistries.SOUND_EVENT.wrapAsHolder(ModSounds.PHYSCANNON_TOOHEAVY),
            SoundSource.PLAYERS,
            0.75F,
            1.0F
         );
   }

   private static boolean hasPortalOnBlock(ServerLevel world, BlockPos pos) {
      AABB blockBox = new AABB(pos).inflate(0.05);
      AABB searchBox = blockBox.inflate(1.5);

      for (PortalEntity portal : world.getEntitiesOfClass(PortalEntity.class, searchBox, portalx -> !portalx.isRemoved())) {
         if (portal.getTeleportBox().intersects(blockBox)) {
            return true;
         }
      }

      return false;
   }

   private static void keepHeldEntityStable(Entity entity) {
      entity.fallDistance = 0.0;
      entity.needsSync = true;
      if (entity instanceof FallingBlockEntity fallingBlock) {
         fallingBlock.dropItem = true;
         fallingBlock.time = 1;
      }
   }

   private static void releaseHeldEntity(Entity entity, boolean playSound) {
      entity.setNoGravity(false);
      entity.setOnGround(false);
      entity.fallDistance = 0.0;
      entity.setDeltaMovement(0.0, -0.08, 0.0);
      entity.needsSync = true;
      entity.removeTag("portalgun_grabbed");
      if (entity instanceof FallingBlockEntity fallingBlock) {
         fallingBlock.dropItem = true;
         fallingBlock.time = 1;
      }

      if (playSound) {
         entity.level()
            .playSound(
               null,
               entity.getX(),
               entity.getY(),
               entity.getZ(),
               BuiltInRegistries.SOUND_EVENT.wrapAsHolder(ModSounds.PHYSCANNON_DROP),
               SoundSource.PLAYERS,
               0.75F,
               1.0F
            );
      }
   }

   private static LivingEntity findGrabTarget(ServerPlayer player) {
      Vec3 start = player.getEyePosition(1.0F);
      Vec3 dir = player.getViewVector(1.0F);
      Vec3 end = start.add(dir.scale(8.0));
      AABB box = player.getBoundingBox().expandTowards(dir.scale(8.0)).inflate(1.0);
      HitResult blockHit = player.pick(8.0, 0.0F, false);
      double maxEntityDistance = 64.0;
      if (blockHit.getType() == Type.BLOCK) {
         maxEntityDistance = start.distanceToSqr(blockHit.getLocation());
      }

      LivingEntity best = null;
      double bestDist = maxEntityDistance;

      for (Entity candidate : player.level().getEntities(player, box, e -> e instanceof LivingEntity && !(e instanceof Player) && e.isAlive())) {
         Optional<Vec3> hit = candidate.getBoundingBox().clip(start, end);
         if (!hit.isEmpty()) {
            double dist = start.distanceToSqr(hit.get());
            if (dist < bestDist) {
               bestDist = dist;
               best = (LivingEntity)candidate;
            }
         }
      }

      return best;
   }

   private static void setGrabVisualState(ServerPlayer player, boolean active) {
      if (player != null) {
         PortalGunItem.setGrabActive(player.getMainHandItem(), active);
         PortalGunItem.setGrabActive(player.getOffhandItem(), false);
         sendGrabStatus(player, active);
      }
   }

   private static void throwGrab(ServerPlayer player) {
      if (!hasPortalGunInMainHand(player)) {
         setGrabVisualState(player, false);
      } else {
         PortalGunMod.GrabState state = GRABBED.remove(player.getUUID());
         if (state == null) {
            setGrabVisualState(player, false);
         } else {
            Entity entity = findEntityByUuid(player.level().getServer(), state.entityUuid);
            if (entity != null) {
               throwHeldEntity(player, entity);
            }

            setGrabVisualState(player, false);
         }
      }
   }

   private static void throwHeldEntity(ServerPlayer player, Entity entity) {
      Vec3 throwVelocity = player.getViewVector(1.0F).normalize().scale(1.8).add(0.0, 0.15, 0.0);
      entity.setNoGravity(false);
      entity.setOnGround(false);
      entity.fallDistance = 0.0;
      entity.setDeltaMovement(throwVelocity);
      entity.needsSync = true;
      entity.removeTag("portalgun_grabbed");
      if (entity instanceof FallingBlockEntity fallingBlock) {
         fallingBlock.dropItem = true;
         fallingBlock.time = 1;
      }
   }

   private static void sendGrabStatus(ServerPlayer player, boolean active) {
      if (player != null) {
         ServerPlayNetworking.send(player, new GrabStatusPayload(active));
      }
   }

   private static Entity findEntityByUuid(MinecraftServer server, UUID uuid) {
      for (ServerLevel world : server.getAllLevels()) {
         Entity entity = world.getEntity(uuid);
         if (entity != null) {
            return entity;
         }
      }

      return null;
   }

   private record GrabState(UUID entityUuid) {
      private GrabState {
      }
   }
}
