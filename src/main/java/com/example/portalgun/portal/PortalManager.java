package com.example.portalgun.portal;

import com.example.portalgun.ModEntities;
import com.example.portalgun.ModSounds;
import com.example.portalgun.PortalGunMod;
import com.example.portalgun.mixin.PersistentProjectileEntityAccessor;
import com.example.portalgun.network.PortalStatusPayload;
import com.example.portalgun.network.PortalViewPayload;
import com.example.portalgun.util.PortalParticles;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.Relative;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.server.MinecraftServer;
import org.joml.Vector3f;

public class PortalManager {
   private static final double BLACK_HOLE_RADIUS = 11.0;
   private static final double BLACK_HOLE_EVENT_HORIZON_EXPANSION = 0.18;
   private static final double BLACK_HOLE_FAR_PULL_STRENGTH = 0.045;
   private static final double BLACK_HOLE_CLOSE_PULL_STRENGTH = 0.52;
   private static final double BLACK_HOLE_FAR_MAX_PULL_SPEED = 0.55;
   private static final double BLACK_HOLE_CLOSE_MAX_PULL_SPEED = 2.45;
   private static final double BLACK_HOLE_FRONT_PLANE_EPSILON = 0.12;
   private static final int BLACK_HOLE_BLOCK_SCAN_RADIUS = 9;
   private static final int BLACK_HOLE_BLOCKS_PER_SCAN = 4;
   private static final int BLACK_HOLE_BLOCK_SCAN_INTERVAL = 4;
   private static final double VIEW_DISTANCE = 48.0;
   private static final double VIEW_SURFACE_OFFSET = 0.08;
   private static final double STATIONARY_CAMERA_DISTANCE = 1.25;
   private static final double PORTAL_VIEW_WIDTH = 1.0;
   private static final double PORTAL_VIEW_HEIGHT = 2.0;
   private static final int VIEW_REFRESH_TICKS = 40;
   private static final double CROSS_DIMENSION_VIEW_SYNC_DISTANCE_SQUARED = 9216.0;
   private static final double OVAL_OPENING_HALF_WIDTH = 0.37;
   private static final double OVAL_OPENING_HALF_HEIGHT = 0.87;
   private static final Map<UUID, PortalManager.PortalPair> PORTALS = new HashMap<>();

   public PortalManager() {
   }

   private static PortalManager.PortalPair pair(UUID owner) {
      return PORTALS.computeIfAbsent(owner, u -> new PortalManager.PortalPair());
   }

   private static void storeAndLink(ServerPlayer owner, PortalType type, PortalEntity portal) {
      PortalManager.PortalPair p = pair(owner.getUUID());
      PortalEntity oldPortal;
      if (type == PortalType.BLUE) {
         oldPortal = p.blue;
         p.blue = portal;
         p.blueMoon = false;
      } else {
         oldPortal = p.orange;
         p.orange = portal;
         p.orangeMoon = false;
      }

      if (oldPortal != null && !oldPortal.isRemoved()) {
         oldPortal.discard();
      }

      linkPair(p);
      sendStatus(owner);
   }

   public static void clearAll(MinecraftServer server) {
      if (server != null) {
         PORTALS.clear();

         for (ServerLevel world : server.getAllLevels()) {
            AABB huge = new AABB(-3.0E7, world.getMinY(), -3.0E7, 3.0E7, world.getMaxY() + 1, 3.0E7);

            for (PortalEntity p : world.getEntitiesOfClass(PortalEntity.class, huge, e -> true)) {
               p.discard();
            }
         }

         for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, new PortalStatusPayload(false, false));
         }
      }
   }

   private static void linkPair(PortalManager.PortalPair p) {
      boolean blueOk = p.blue != null && !p.blue.isRemoved();
      boolean orangeOk = p.orange != null && !p.orange.isRemoved();
      if (blueOk) {
         p.blue.setLinked(null);
         p.blue.setBlackHole(false);
      }

      if (orangeOk) {
         p.orange.setLinked(null);
         p.orange.setBlackHole(false);
      }

      if (blueOk && orangeOk) {
         p.blue.setLinked(p.orange);
         p.orange.setLinked(p.blue);
      } else {
         if (blueOk && p.orangeMoon) {
            p.blue.setBlackHole(true);
         }

         if (orangeOk && p.blueMoon) {
            p.orange.setBlackHole(true);
         }
      }
   }

   private static boolean isAlive(PortalEntity portal) {
      return portal != null && !portal.isRemoved();
   }

   private static void sendStatus(ServerPlayer player) {
      PortalManager.PortalPair p = PORTALS.get(player.getUUID());
      boolean hasBlue = p != null && (isAlive(p.blue) || p.blueMoon);
      boolean hasOrange = p != null && (isAlive(p.orange) || p.orangeMoon);
      ServerPlayNetworking.send(player, new PortalStatusPayload(hasBlue, hasOrange));
   }

   public static void sendStatusForPlayer(ServerPlayer player) {
      if (player != null) {
         sendStatus(player);
      }
   }

   public static void syncCrossDimensionViews(MinecraftServer server) {
      if (server != null && server.overworld().getGameTime() % 40L == 0L) {
         for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PortalManager.PortalPair pair = PORTALS.get(player.getUUID());
            if (pair != null) {
               sendCrossDimensionView(player, pair.blue, pair.orange);
               sendCrossDimensionView(player, pair.orange, pair.blue);
            }
         }
      }
   }

   private static void sendCrossDimensionView(ServerPlayer player, PortalEntity source, PortalEntity target) {
      if (isAlive(source) && isAlive(target)) {
         if (source.level() instanceof ServerLevel sourceWorld
            && target.level() instanceof ServerLevel targetWorld
            && sourceWorld != targetWorld
            && player.level() == sourceWorld) {
            if (!(source.distanceToSqr(player) > 9216.0)) {
               ServerPlayNetworking.send(player, new PortalViewPayload(source.getId(), createPortalViewPixels(targetWorld, target)));
            }
         }
      }
   }

   public static void placeMoonPortal(ServerPlayer owner, PortalType type) {
      if (owner != null) {
         PortalManager.PortalPair p = pair(owner.getUUID());
         PortalEntity oldPortal;
         if (type == PortalType.BLUE) {
            oldPortal = p.blue;
            p.blue = null;
            p.blueMoon = true;
         } else {
            oldPortal = p.orange;
            p.orange = null;
            p.orangeMoon = true;
         }

         if (oldPortal != null && !oldPortal.isRemoved()) {
            oldPortal.discard();
         }

         linkPair(p);
         sendStatus(owner);
         ServerLevel world = owner.level();
         world.playSound(
            null,
            owner.getX(),
            owner.getY(),
            owner.getZ(),
            BuiltInRegistries.SOUND_EVENT.wrapAsHolder(type == PortalType.BLUE ? ModSounds.PORTAL_SHOOT_BLUE : ModSounds.PORTAL_SHOOT_ORANGE),
            SoundSource.PLAYERS,
            0.85F,
            type == PortalType.BLUE ? 0.65F : 0.55F
         );
         world.sendParticles(
            ParticleTypes.REVERSE_PORTAL,
            owner.getX(),
            owner.getY() + owner.getEyeHeight(owner.getPose()),
            owner.getZ(),
            24,
            0.25,
            0.25,
            0.25,
            0.03
         );
      }
   }

   public static void onPortalRemoved(PortalEntity portal) {
      if (portal != null && portal.level() instanceof ServerLevel world) {
         UUID owner = portal.getOwner();
         if (owner != null) {
            PortalManager.PortalPair p = PORTALS.get(owner);
            if (p != null) {
               boolean changed = false;
               if (p.blue == portal) {
                  p.blue = null;
                  changed = true;
               }

               if (p.orange == portal) {
                  p.orange = null;
                  changed = true;
               }

               if (changed) {
                  linkPair(p);
                  if (!isAlive(p.blue) && !isAlive(p.orange) && !p.blueMoon && !p.orangeMoon) {
                     PORTALS.remove(owner);
                  }

                  ServerPlayer player = world.getServer().getPlayerList().getPlayer(owner);
                  if (player != null) {
                     sendStatus(player);
                  }
               }
            }
         }
      }
   }

   public static void placePortal(ServerLevel world, ServerPlayer owner, BlockPos pos, Direction face, PortalType type, float yaw) {
      if (world != null && owner != null) {
         PortalEntity portal = new PortalEntity(ModEntities.PORTAL, world);
         portal.setBacking(pos, pos.above());
         portal.setOwner(owner.getUUID());
         double x = pos.getX() + 0.5;
         // Port change: the original used getY() + 0.5, which put wall portals half a block above their two backing blocks.
         double y = pos.getY();
         double z = pos.getZ() + 0.5;
         double offset = 0.503;
         x += face.getStepX() * offset;
         y += face.getStepY() * offset;
         z += face.getStepZ() * offset;

         float placeYaw = switch (face) {
            case NORTH -> 180.0F;
            case SOUTH -> 0.0F;
            case WEST -> 90.0F;
            case EAST -> -90.0F;
            default -> yaw;
         };
         portal.snapTo(x, y, z, placeYaw, 0.0F);
         portal.setFacing(face);
         portal.setPortalType(type);
         boolean spawned = world.addFreshEntity(portal);
         PortalGunMod.LOGGER.debug("Spawned {} portal for {}: {}", new Object[]{type, owner.getName().getString(), spawned});
         if (spawned) {
            try {
               float pitch = type == PortalType.BLUE ? 1.2F : 0.9F;
               // Port change: play at the shooter; at the portal the 16-block sound range made distant shots silent.
               world.playSound(
                  null,
                  owner.getX(),
                  owner.getY(),
                  owner.getZ(),
                  BuiltInRegistries.SOUND_EVENT.wrapAsHolder(type == PortalType.BLUE ? ModSounds.PORTAL_SHOOT_BLUE : ModSounds.PORTAL_SHOOT_ORANGE),
                  SoundSource.PLAYERS,
                  1.0F,
                  pitch
               );
            } catch (Exception var18) {
            }

            storeAndLink(owner, type, portal);
         }
      }
   }

   public static void placePortalFloorCeiling(
      ServerLevel world, ServerPlayer owner, BlockPos hitBlock, Direction face, PortalType type, float snappedYaw, Vec3 hitPos
   ) {
      if (world != null && owner != null) {
         if (face == Direction.UP || face == Direction.DOWN) {
            Direction axis = Direction.fromYRot(snappedYaw);
            if (axis == Direction.UP || axis == Direction.DOWN) {
               axis = Direction.SOUTH;
            }

            double localX = hitPos.x - hitBlock.getX();
            double localZ = hitPos.z - hitBlock.getZ();
            double centerX = hitBlock.getX() + 0.5;
            double centerZ = hitBlock.getZ() + 0.5;
            BlockPos otherSurface;
            if (axis != Direction.EAST && axis != Direction.WEST) {
               boolean useSouth = localZ >= 0.5;
               otherSurface = useSouth ? hitBlock.south() : hitBlock.north();
               centerZ = hitBlock.getZ() + (useSouth ? 1.0 : 0.0);
               centerX = hitBlock.getX() + 0.5;
            } else {
               boolean useEast = localX >= 0.5;
               otherSurface = useEast ? hitBlock.east() : hitBlock.west();
               centerX = hitBlock.getX() + (useEast ? 1.0 : 0.0);
               centerZ = hitBlock.getZ() + 0.5;
            }

            if (!world.getBlockState(hitBlock).getCollisionShape(world, hitBlock).isEmpty()
               && !world.getBlockState(otherSurface).getCollisionShape(world, otherSurface).isEmpty()) {
               double eps = 0.003;
               double y = face == Direction.UP ? hitBlock.getY() + 1.0 + eps : hitBlock.getY() - eps;
               PortalEntity portal = new PortalEntity(ModEntities.PORTAL, world);
               portal.setBacking(hitBlock, otherSurface);
               portal.setOwner(owner.getUUID());
               portal.snapTo(centerX, y, centerZ, snappedYaw, 0.0F);
               portal.setFacing(face);
               portal.setPortalType(type);
               boolean spawned = world.addFreshEntity(portal);
               PortalGunMod.LOGGER.debug("Spawned {} portal for {}: {}", new Object[]{type, owner.getName().getString(), spawned});
               if (spawned) {
                  try {
                     float pitch = type == PortalType.BLUE ? 1.2F : 0.9F;
                     world.playSound(
                        null,
                        owner.getX(),
                        owner.getY(),
                        owner.getZ(),
                        BuiltInRegistries.SOUND_EVENT.wrapAsHolder(type == PortalType.BLUE ? ModSounds.PORTAL_SHOOT_BLUE : ModSounds.PORTAL_SHOOT_ORANGE),
                        SoundSource.PLAYERS,
                        1.0F,
                        pitch
                     );
                  } catch (Exception var24) {
                  }

                  storeAndLink(owner, type, portal);
               }
            } else {
               PortalGunMod.LOGGER.debug("Floor/ceiling portal surface was not 2 blocks wide.");
            }
         }
      }
   }

   public static boolean teleport(Entity entity, PortalEntity target) {
      if (entity == null || target == null) {
         return false;
      }

      if (!entity.isAlive()) {
         return false;
      }

      if (entity.isPassenger() || entity.isVehicle()) {
         return false;
      }

      if (target.isBlackHole()) {
         consumeBlackHoleEntity(entity, target);
         return true;
      }

      if (entity.isOnPortalCooldown()) {
         return false;
      }

      if (entity.level() instanceof ServerLevel sourceWorld) {
         if (!(target.level() instanceof ServerLevel targetWorld)) {
            return false;
         } else {
            PortalEntity source = target.getLinked();
            if (source != null && !source.isRemoved()) {
               Vec3 entrancePos = new Vec3(source.getX(), source.getY(), source.getZ());
               Vec3 baseVel = getTeleportVelocity(entity);
               Vec3 vel = rotateVectorThroughPortal(baseVel, source, target);
               Vec3 look = entity instanceof Projectile && vel.lengthSqr() > 1.0E-6
                  ? vel.normalize()
                  : rotateVectorThroughPortal(entity.getViewVector(1.0F), source, target);
               Vec3 exitPos = findSafeExit(targetWorld, entity, target);
               Direction out = target.getNearestViewDirection();
               if (out == null) {
                  out = Direction.SOUTH;
               }

               Vec3 outNormal = new Vec3(out.getStepX(), out.getStepY(), out.getStepZ());
               float exitYaw = entity.getYRot();
               float exitPitch = entity.getXRot();
               if (look.lengthSqr() > 1.0E-6) {
                  exitYaw = (float)(Mth.atan2(look.z, look.x) * 57.295776) - 90.0F;
                  double horiz = Math.sqrt(look.x * look.x + look.z * look.z);
                  exitPitch = (float)(-Mth.atan2(look.y, horiz) * 57.295776);
                  exitYaw = Mth.wrapDegrees(exitYaw);
               }

               Entity var24;
               if (entity instanceof ServerPlayer player) {
                  if (sourceWorld != targetWorld) {
                     player.teleportTo(
                        targetWorld, exitPos.x, exitPos.y, exitPos.z, EnumSet.noneOf(Relative.class), exitYaw, exitPitch, false
                     );
                  } else {
                     player.connection.teleport(exitPos.x, exitPos.y, exitPos.z, exitYaw, exitPitch);
                  }

                  var24 = player;
               } else {
                  if (sourceWorld != targetWorld) {
                     TeleportTransition teleportTarget = new TeleportTransition(targetWorld, exitPos, vel, exitYaw, exitPitch, TeleportTransition.PLACE_PORTAL_TICKET);
                     var24 = entity.teleport(teleportTarget);
                     if (var24 == null) {
                        return false;
                     }
                  } else {
                     entity.teleportTo(exitPos.x, exitPos.y, exitPos.z);
                     var24 = entity;
                  }

                  var24.setYRot(exitYaw);
                  var24.setXRot(exitPitch);
                  if (var24 instanceof LivingEntity living) {
                     living.setYHeadRot(exitYaw);
                     living.setYBodyRot(exitYaw);
                  }
               }

               playTeleportSounds(sourceWorld, entrancePos, targetWorld, exitPos, (Entity)var24);
               Vector3f color = target.getPortalType() == PortalType.ORANGE ? new Vector3f(1.0F, 0.65F, 0.25F) : new Vector3f(0.3F, 0.65F, 1.0F);
               ParticleOptions dust = PortalParticles.createDust(color, 1.0F);
               targetWorld.sendParticles(dust, exitPos.x, exitPos.y + 0.9, exitPos.z, 24, 0.35, 0.6, 0.35, 0.02);
               boolean sprinting = entity instanceof LivingEntity living && living.isSprinting();
               double boost = 0.0;
               if (vel.y > 0.1) {
                  boost += 0.6;
               }

               if (sprinting) {
                  boost += 0.5;
               }

               if (boost > 0.0) {
                  vel = vel.add(outNormal.scale(boost));
               }

               if (var24 instanceof AbstractArrow projectile) {
                  resetProjectileState(projectile);
               }

               applyExitVelocity((Entity)var24, vel);
               var24.setPortalCooldown();
               return true;
            } else {
               return false;
            }
         }
      } else {
         return false;
      }
   }

   public static boolean teleportProjectileCollision(Projectile projectile, HitResult hit) {
      if (projectile != null && hit != null && projectile.isAlive()) {
         if (hit.getType() == Type.BLOCK && !projectile.isOnPortalCooldown()) {
            if (projectile.level() instanceof ServerLevel world) {
               Vec3 var8 = hit.getLocation();
               AABB searchBox = new AABB(var8, var8).inflate(2.5);

               for (PortalEntity portal : world.getEntitiesOfClass(PortalEntity.class, searchBox, portalx -> hasUsableLink(portalx) || isBlackHole(portalx))) {
                  if (portal.getTeleportBox().inflate(0.2).contains(var8)) {
                     if (!portal.isBlackHole()) {
                        return teleport(projectile, portal.getLinked());
                     }

                     Vec3 center = portalCenter(portal);
                     if (isPointInFrontOfPortal(entityCenter(projectile), center, portalNormal(portal))) {
                        consumeBlackHoleEntity(projectile, portal);
                        return true;
                     }
                  }
               }

               return false;
            } else {
               return false;
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   public static void tickBlackHole(PortalEntity portal) {
      if (portal != null && !portal.isRemoved() && portal.level() instanceof ServerLevel world) {
         Vec3 var8 = portalCenter(portal);
         Vec3 normal = portalNormal(portal);
         AABB pullBox = new AABB(var8, var8).inflate(11.0);
         AABB eventHorizon = portal.getTeleportBox().inflate(0.18);

         for (Entity entity : world.getEntities(
            portal, pullBox, entityx -> entityx.isAlive() && !(entityx instanceof PortalEntity) && !(entityx instanceof ServerPlayer)
         )) {
            applyBlackHolePull(portal, entity, var8, normal, eventHorizon);
         }

         for (ServerPlayer player : world.getServer().getPlayerList().getPlayers()) {
            if (player.level() == world && player.isAlive() && pullBox.intersects(player.getBoundingBox())) {
               applyBlackHolePull(portal, player, var8, normal, eventHorizon);
            }
         }

         if (world.getGameTime() % 4L == 0L) {
            pullNearbyBlocksIntoBlackHole(world, portal, var8, normal);
         }

         world.sendParticles(ParticleTypes.SQUID_INK, var8.x, var8.y, var8.z, 5, 0.25, 0.35, 0.25, 0.01);
         world.sendParticles(ParticleTypes.REVERSE_PORTAL, var8.x, var8.y, var8.z, 14, 0.7, 0.7, 0.7, 0.06);
      }
   }

   private static void applyBlackHolePull(PortalEntity portal, Entity entity, Vec3 center, Vec3 normal, AABB eventHorizon) {
      Vec3 entityCenter = entityCenter(entity);
      if (isPointInFrontOfPortal(entityCenter, center, normal) && !(center.distanceToSqr(entityCenter) > 121.0)) {
         if (eventHorizon.intersects(entity.getBoundingBox())) {
            consumeBlackHoleEntity(entity, portal);
         } else {
            pullTowardBlackHole(entity, center, entityCenter);
         }
      }
   }

   private static void pullTowardBlackHole(Entity entity, Vec3 center) {
      pullTowardBlackHole(entity, center, entityCenter(entity));
   }

   private static void pullTowardBlackHole(Entity entity, Vec3 center, Vec3 entityCenter) {
      Vec3 toCenter = center.subtract(entityCenter);
      double distance = Math.max(0.35, toCenter.length());
      double closeness = Math.max(0.0, Math.min(1.0, 1.0 - distance / 11.0));
      double curvedCloseness = closeness * closeness;
      double pullStrength = 0.045 + 0.47500000000000003 * curvedCloseness;
      double maxPullSpeed = 0.55 + 1.9000000000000001 * curvedCloseness;
      Vec3 pull = toCenter.normalize().scale(pullStrength);
      Vec3 velocity = entity.getDeltaMovement().add(pull);
      if (velocity.length() > maxPullSpeed) {
         velocity = velocity.normalize().scale(maxPullSpeed);
      }

      entity.setDeltaMovement(velocity);
      entity.needsSync = true;
      entity.fallDistance = 0.0;
      if (entity instanceof ServerPlayer player) {
         player.connection.send(new ClientboundSetEntityMotionPacket(player));
      }
   }

   private static void pullNearbyBlocksIntoBlackHole(ServerLevel world, PortalEntity portal, Vec3 center, Vec3 normal) {
      BlockPos origin = BlockPos.containing(center);
      double maxDistanceSquared = 81.0;
      List<PortalManager.BlockPullCandidate> candidates = new ArrayList<>();

      for (int dy = -9; dy <= 9; dy++) {
         for (int dx = -9; dx <= 9; dx++) {
            for (int dz = -9; dz <= 9; dz++) {
               BlockPos pos = origin.offset(dx, dy, dz);
               Vec3 blockCenter = Vec3.atCenterOf(pos);
               double distanceSquared = center.distanceToSqr(blockCenter);
               if (!portal.isBackingBlock(pos)
                  && !(distanceSquared > maxDistanceSquared)
                  && isPointInFrontOfPortal(blockCenter, center, normal)
                  && world.isLoaded(pos)) {
                  BlockState state = world.getBlockState(pos);
                  if (isBlackHoleMovableBlock(world, pos, state)) {
                     candidates.add(new PortalManager.BlockPullCandidate(pos, distanceSquared));
                  }
               }
            }
         }
      }

      candidates.sort(Comparator.comparingDouble(PortalManager.BlockPullCandidate::distanceSquared));
      int moved = 0;

      for (PortalManager.BlockPullCandidate candidate : candidates) {
         if (moved >= 4) {
            break;
         }

         BlockState state = world.getBlockState(candidate.pos());
         if (isBlackHoleMovableBlock(world, candidate.pos(), state)) {
            FallingBlockEntity fallingBlock = FallingBlockEntity.fall(world, candidate.pos(), state);
            fallingBlock.dropItem = false;
            fallingBlock.time = 1;
            pullTowardBlackHole(fallingBlock, center);
            moved++;
         }
      }
   }

   private static boolean isBlackHoleMovableBlock(ServerLevel world, BlockPos pos, BlockState state) {
      if (state.isAir() || state.hasBlockEntity()) {
         return false;
      } else if (!state.is(Blocks.NETHER_PORTAL)
         && !state.is(Blocks.END_PORTAL)
         && !state.is(Blocks.END_GATEWAY)
         && !state.is(Blocks.END_PORTAL_FRAME)) {
         return state.getDestroySpeed(world, pos) < 0.0F ? false : state.isCollisionShapeFullBlock(world, pos) && state.canOcclude();
      } else {
         return false;
      }
   }

   private static void consumeBlackHoleEntity(Entity entity, PortalEntity portal) {
      if (entity != null && portal != null && portal.level() instanceof ServerLevel world) {
         Vec3 var5 = portalCenter(portal);
         world.sendParticles(ParticleTypes.SQUID_INK, var5.x, var5.y, var5.z, 18, 0.2, 0.3, 0.2, 0.04);
         world.playSound(
            null,
            var5.x,
            var5.y,
            var5.z,
            BuiltInRegistries.SOUND_EVENT.wrapAsHolder(ModSounds.PORTAL_FIZZLE),
            SoundSource.PLAYERS,
            0.35F,
            0.55F
         );
         if (!(entity instanceof ServerPlayer player && (player.isCreative() || player.isSpectator()))) {
            if (entity instanceof LivingEntity living) {
               living.kill(world);
            } else {
               entity.discard();
            }
         } else {
            player.setDeltaMovement(Vec3.ZERO);
         }
      }
   }

   private static Vec3 portalCenter(PortalEntity portal) {
      Direction facing = portal.getNearestViewDirection();
      double y = portal.getY();
      if (facing != Direction.UP && facing != Direction.DOWN) {
         y++;
      }

      return new Vec3(portal.getX(), y, portal.getZ());
   }

   private static Vec3 portalNormal(PortalEntity portal) {
      Direction facing = portal.getNearestViewDirection();
      if (facing == null) {
         facing = Direction.SOUTH;
      }

      Vec3 normal = new Vec3(facing.getStepX(), facing.getStepY(), facing.getStepZ());
      return normal.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 0.0, 1.0) : normal.normalize();
   }

   private static Vec3 entityCenter(Entity entity) {
      return new Vec3(entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ());
   }

   private static int[] createPortalViewPixels(ServerLevel world, PortalEntity linkedPortal) {
      int[] pixels = new int[7776];
      PortalManager.PortalBasis linkedBasis = basisForPortal(linkedPortal);
      Vec3 linkedCenter = portalCenter(linkedPortal);
      Vec3 stationaryCamera = linkedCenter.subtract(linkedBasis.forward.scale(1.25));

      for (int row = 0; row < 108; row++) {
         for (int col = 0; col < 72; col++) {
            int index = row * 72 + col;
            double x = ((col + 0.5) / 72.0 - 0.5) * 1.0;
            double y = (0.5 - (row + 0.5) / 108.0) * 2.0;
            if (!insideInsetOvalCenter(x, y)) {
               pixels[index] = 0;
            } else {
               Vec3 linkedSurfacePoint = linkedCenter.add(linkedBasis.right.scale(x)).add(linkedBasis.up.scale(y));
               Vec3 ray = linkedSurfacePoint.subtract(stationaryCamera);
               if (ray.lengthSqr() < 1.0E-6) {
                  ray = linkedBasis.forward;
               } else {
                  ray = ray.normalize();
               }

               Vec3 rayStart = linkedSurfacePoint.add(ray.scale(0.08));
               pixels[index] = sampleViewColor(world, rayStart, ray);
            }
         }
      }

      return pixels;
   }

   private static int sampleViewColor(ServerLevel world, Vec3 start, Vec3 ray) {
      Vec3 end = start.add(ray.scale(48.0));
      BlockHitResult hit = world.clip(new ClipContext(start, end, Block.OUTLINE, Fluid.ANY, CollisionContext.empty()));
      if (hit.getType() != Type.BLOCK) {
         return skyColor(world, ray);
      }

      BlockPos pos = hit.getBlockPos();
      BlockState blockState = world.getBlockState(pos);
      if (blockState.isAir()) {
         return skyColor(world, ray);
      }

      double hitDistance = hit.getLocation().distanceTo(start);
      MapColor mapColor = blockState.getMapColor(world, pos);
      int color = mapColor == MapColor.NONE ? blockState.getBlock().defaultMapColor().col : mapColor.col;
      double distanceShade = Math.max(0.36, 1.0 - hitDistance / 60.0);
      double sideShade = com.example.portalgun.util.PortalShade.faceShade(hit.getDirection());
      double lightShade = 0.52 + world.getRawBrightness(pos, 0) / 15.0 * 0.32;
      double shade = Math.max(0.3, Math.min(1.0, distanceShade * (sideShade * 0.32 + lightShade)));
      return quantizeColor(applyShade(0xFF000000 | color, shade));
   }

   private static boolean insideInsetOvalCenter(double x, double y) {
      double nx = x / 0.37;
      double ny = y / 0.87;
      return nx * nx + ny * ny <= 1.0;
   }

   private static int skyColor(ServerLevel world, Vec3 ray) {
      int horizon;
      int zenith;
      if (world.dimension() == Level.NETHER) {
         horizon = -12970734;
         zenith = -15005946;
      } else if (world.dimension() == Level.END) {
         horizon = -14936023;
         zenith = -16251375;
      } else if (world.isDarkOutside()) {
         horizon = -15261382;
         zenith = -16248545;
      } else {
         horizon = -3548952;
         zenith = -9525288;
      }

      double amount = Math.max(0.0, Math.min(1.0, ray.y * 0.5 + 0.5));
      return quantizeColor(blendColor(horizon, zenith, amount));
   }

   private static int applyShade(int argb, double shade) {
      int a = argb >>> 24 & 0xFF;
      int r = (int)((argb >>> 16 & 0xFF) * shade);
      int g = (int)((argb >>> 8 & 0xFF) * shade);
      int b = (int)((argb & 0xFF) * shade);
      return a << 24 | clampColor(r) << 16 | clampColor(g) << 8 | clampColor(b);
   }

   private static int quantizeColor(int argb) {
      int a = argb >>> 24 & 0xFF;
      int r = quantizeChannel(argb >>> 16 & 0xFF);
      int g = quantizeChannel(argb >>> 8 & 0xFF);
      int b = quantizeChannel(argb & 0xFF);
      return a << 24 | r << 16 | g << 8 | b;
   }

   private static int quantizeChannel(int value) {
      return clampColor(value / 8 * 8);
   }

   private static int blendColor(int from, int to, double amount) {
      int a = blendChannel(from >>> 24 & 0xFF, to >>> 24 & 0xFF, amount);
      int r = blendChannel(from >>> 16 & 0xFF, to >>> 16 & 0xFF, amount);
      int g = blendChannel(from >>> 8 & 0xFF, to >>> 8 & 0xFF, amount);
      int b = blendChannel(from & 0xFF, to & 0xFF, amount);
      return a << 24 | r << 16 | g << 8 | b;
   }

   private static int blendChannel(int from, int to, double amount) {
      return clampColor((int)Math.round(from + (to - from) * amount));
   }

   private static int clampColor(int value) {
      return Math.max(0, Math.min(255, value));
   }

   private static boolean isPointInFrontOfPortal(Vec3 point, Vec3 portalCenter, Vec3 portalNormal) {
      return point.subtract(portalCenter).dot(portalNormal) > 0.12;
   }

   private static void playTeleportSounds(ServerLevel sourceWorld, Vec3 entrancePos, ServerLevel targetWorld, Vec3 exitPos, Entity teleported) {
      playTeleportSound(sourceWorld, entrancePos, 0.55F, 1.05F, null);
      if (teleported instanceof ServerPlayer player) {
         playTeleportSound(targetWorld, exitPos, 0.9F, 1.2F, player);
         player.connection
            .send(
               new ClientboundSoundPacket(
                  BuiltInRegistries.SOUND_EVENT.wrapAsHolder(ModSounds.PORTAL_TELEPORT),
                  SoundSource.PLAYERS,
                  exitPos.x,
                  exitPos.y,
                  exitPos.z,
                  0.9F,
                  1.2F,
                  player.getRandom().nextLong()
               )
            );
      } else {
         playTeleportSound(targetWorld, exitPos, 0.75F, 1.2F, null);
      }
   }

   private static void playTeleportSound(ServerLevel world, Vec3 pos, float volume, float pitch, Entity except) {
      world.playSound(
         except,
         pos.x,
         pos.y,
         pos.z,
         BuiltInRegistries.SOUND_EVENT.wrapAsHolder(ModSounds.PORTAL_TELEPORT),
         SoundSource.PLAYERS,
         volume,
         pitch
      );
   }

   private static boolean hasUsableLink(PortalEntity portal) {
      return portal != null && !portal.isRemoved() && portal.getLinked() != null && !portal.getLinked().isRemoved();
   }

   private static boolean isBlackHole(PortalEntity portal) {
      return portal != null && !portal.isRemoved() && portal.isBlackHole();
   }

   private static Vec3 getTeleportVelocity(Entity entity) {
      Vec3 baseVel = entity.getDeltaMovement();
      if (!(entity instanceof Projectile)) {
         return baseVel;
      }

      Vec3 lastTickMovement = entity.getKnownSpeed();
      if (lastTickMovement.lengthSqr() > baseVel.lengthSqr()) {
         baseVel = lastTickMovement;
      }

      Vec3 publicLastPositionMovement = new Vec3(
         entity.getX() - entity.xo, entity.getY() - entity.yo, entity.getZ() - entity.zo
      );
      if (publicLastPositionMovement.lengthSqr() > baseVel.lengthSqr()) {
         baseVel = publicLastPositionMovement;
      }

      return baseVel;
   }

   private static void applyExitVelocity(Entity entity, Vec3 velocity) {
      if (entity instanceof Projectile projectile) {
         float speed = (float)velocity.length();
         if (speed > 1.0E-6F) {
            projectile.shoot(velocity.x, velocity.y, velocity.z, speed, 0.0F);
         } else {
            projectile.setDeltaMovement(velocity);
         }
      } else {
         entity.setDeltaMovement(velocity);
      }
   }

   private static Vec3 rotateVectorThroughPortal(Vec3 vec, PortalEntity source, PortalEntity target) {
      PortalManager.PortalBasis src = basisForPortal(source);
      PortalManager.PortalBasis dst = basisForPortal(target);
      double x = vec.dot(src.right);
      double y = vec.dot(src.up);
      double z = vec.dot(src.forward);
      z = -z;
      return dst.right.scale(x).add(dst.up.scale(y)).add(dst.forward.scale(z));
   }

   private static PortalManager.PortalBasis basisForPortal(PortalEntity portal) {
      Direction facing = portal.getNearestViewDirection();
      if (facing == null) {
         facing = Direction.SOUTH;
      }

      Vec3 forward = new Vec3(facing.getStepX(), facing.getStepY(), facing.getStepZ());
      if (forward.lengthSqr() < 1.0E-6) {
         forward = new Vec3(0.0, 0.0, 1.0);
      }

      forward = forward.normalize();
      Vec3 up;
      if (facing != Direction.UP && facing != Direction.DOWN) {
         up = new Vec3(0.0, 1.0, 0.0);
      } else {
         up = Vec3.directionFromRotation(0.0F, portal.getYRot());
      }

      if (up.lengthSqr() < 1.0E-6) {
         up = new Vec3(0.0, 0.0, 1.0);
      }

      up = up.normalize();
      Vec3 right = forward.cross(up);
      if (right.lengthSqr() < 1.0E-6) {
         right = new Vec3(1.0, 0.0, 0.0);
      }

      right = right.normalize();
      up = right.cross(forward).normalize();
      return new PortalManager.PortalBasis(right, up, forward);
   }

   private static void resetProjectileState(AbstractArrow projectile) {
      projectile.shakeTime = 0;
      PersistentProjectileEntityAccessor accessor = (PersistentProjectileEntityAccessor)projectile;
      accessor.portalgun$setInGround(false);
      accessor.portalgun$setInGroundTime(0);
   }

   private static Vec3 findSafeExit(ServerLevel world, Entity entity, PortalEntity target) {
      Direction out = target.getNearestViewDirection();
      if (out == null) {
         out = Direction.SOUTH;
      }

      Vec3 normal = new Vec3(out.getStepX(), out.getStepY(), out.getStepZ());
      if (normal.lengthSqr() < 1.0E-6) {
         normal = new Vec3(0.0, 0.0, 1.0);
      }

      // Port change: the original shifted wall exits down by 0.5 to compensate for the old +0.5 portal height.
      Vec3 base = new Vec3(target.getX(), target.getY(), target.getZ());

      double minPush = 0.35 + Math.max(entity.getBbWidth(), entity.getBbHeight()) * 0.6;

      for (double push = minPush; push <= 3.0; push += 0.15) {
         Vec3 candidate = base.add(normal.scale(push));
         if (out != Direction.UP && out != Direction.DOWN) {
            for (double dy = 0.0; dy <= 1.0; dy += 0.25) {
               Vec3 test = candidate.add(0.0, dy, 0.0);
               if (isBoxFreeAt(world, entity, test)) {
                  return test;
               }
            }
         } else {
            Vec3[] nudges = new Vec3[]{
               new Vec3(0.0, 0.0, 0.0),
               new Vec3(0.25, 0.0, 0.0),
               new Vec3(-0.25, 0.0, 0.0),
               new Vec3(0.0, 0.0, 0.25),
               new Vec3(0.0, 0.0, -0.25)
            };

            for (Vec3 n : nudges) {
               Vec3 test = candidate.add(n);
               if (isBoxFreeAt(world, entity, test)) {
                  return test;
               }
            }
         }
      }

      return base.add(normal.scale(3.0));
   }

   private static boolean isBoxFreeAt(ServerLevel world, Entity entity, Vec3 pos) {
      AABB moved = entity.getBoundingBox()
         .move(pos.x - entity.getX(), pos.y - entity.getY(), pos.z - entity.getZ());
      return !world.getBlockCollisions(entity, moved).iterator().hasNext();
   }

   public static void clear(ServerLevel world) {
      if (world != null) {
         AABB huge = new AABB(-3.0E7, world.getMinY(), -3.0E7, 3.0E7, world.getMaxY() + 1, 3.0E7);

         for (PortalEntity p : world.getEntitiesOfClass(PortalEntity.class, huge, e -> true)) {
            p.discard();
         }

         pruneRemovedPortals();

         for (ServerPlayer player : world.getServer().getPlayerList().getPlayers()) {
            sendStatus(player);
         }
      }
   }

   public static void clearForPlayer(MinecraftServer server, UUID owner) {
      if (server != null && owner != null) {
         PortalManager.PortalPair p = PORTALS.remove(owner);
         if (p == null) {
            ServerPlayer player = server.getPlayerList().getPlayer(owner);
            if (player != null) {
               ServerPlayNetworking.send(player, new PortalStatusPayload(false, false));
            }
         } else {
            if (p.blue != null && !p.blue.isRemoved()) {
               p.blue.discard();
            }

            if (p.orange != null && !p.orange.isRemoved()) {
               p.orange.discard();
            }

            ServerPlayer player = server.getPlayerList().getPlayer(owner);
            if (player != null) {
               ServerPlayNetworking.send(player, new PortalStatusPayload(false, false));
            }
         }
      }
   }

   private static void pruneRemovedPortals() {
      PORTALS.entrySet().removeIf(entry -> {
         PortalManager.PortalPair p = entry.getValue();
         if (p.blue != null && p.blue.isRemoved()) {
            p.blue = null;
         }

         if (p.orange != null && p.orange.isRemoved()) {
            p.orange = null;
         }

         linkPair(p);
         return !isAlive(p.blue) && !isAlive(p.orange);
      });
   }

   private record BlockPullCandidate(BlockPos pos, double distanceSquared) {
      private BlockPullCandidate {
      }
   }

   private record PortalBasis(Vec3 right, Vec3 up, Vec3 forward) {
      private PortalBasis {
      }
   }

   private static class PortalPair {
      PortalEntity blue;
      PortalEntity orange;
      boolean blueMoon;
      boolean orangeMoon;

      private PortalPair() {
      }
   }
}
