package com.example.portalgun.item;

import com.example.portalgun.ModSounds;
import com.example.portalgun.PortalGunMod;
import com.example.portalgun.portal.PortalEntity;
import com.example.portalgun.portal.PortalManager;
import com.example.portalgun.portal.PortalType;
import com.example.portalgun.util.PortalParticles;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.phys.Vec3;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.phys.HitResult.Type;
import org.joml.Vector3f;

public class PortalGunItem extends Item {
   public static final String ANIM_TICKS_KEY = "portalgun_anim_ticks";
   public static final int ANIM_TICKS = 12;
   public static final String CLEAR_ANIM_TICKS_KEY = "portalgun_clear_anim_ticks";
   public static final int CLEAR_ANIM_TICKS = 14;
   public static final String GRAB_ACTIVE_KEY = "portalgun_grab_active";
   private static final CustomModelData FIRING_MODEL_DATA = new CustomModelData(List.of(1.0F), List.of(), List.of(), List.of());

   public PortalGunItem(Properties settings) {
      super(settings);
   }

   public InteractionResult use(Level world, Player user, InteractionHand hand) {
      ItemStack stack = user.getItemInHand(hand);
      if (world.isClientSide()) {
         return InteractionResult.CONSUME;
      }

      if (user instanceof ServerPlayer serverPlayer) {
         if (PortalGunMod.isGrabbing(serverPlayer)) {
            return InteractionResult.CONSUME;
         }

         boolean fired = tryShootPortal(serverPlayer, PortalType.ORANGE);
         if (fired) {
            markFiring(serverPlayer, stack);
         }

         return (InteractionResult)(fired ? InteractionResult.CONSUME : InteractionResult.FAIL);
      } else {
         return InteractionResult.FAIL;
      }
   }

   public static boolean tryShootPortal(ServerPlayer serverPlayer, PortalType type) {
      ServerLevel world = serverPlayer.level();
      HitResult hit = serverPlayer.pick(64.0, 0.0F, false);
      if (hit.getType() != Type.BLOCK) {
         if (isMoonShot(world, serverPlayer)) {
            PortalManager.placeMoonPortal(serverPlayer, type);
            spawnShootParticles(world, serverPlayer, type);
            return true;
         } else {
            return false;
         }
      } else {
         BlockHitResult bhr = (BlockHitResult)hit;
         BlockPos hitPosBlock = bhr.getBlockPos();
         Direction face = bhr.getDirection();
         Vec3 hitPos = bhr.getLocation();
         if (face != Direction.UP && face != Direction.DOWN) {
            BlockPos base = hitPosBlock;
            double localY = hitPos.y - hitPosBlock.getY();
            if (localY < 0.5) {
               base = hitPosBlock.below();
            }

            if (base.getY() < world.getMinY() || base.getY() + 1 > world.getMaxY()) {
               playFailSound(serverPlayer);
               return false;
            }

            if (isValidPortalSurface(world, base) && isValidPortalSurface(world, base.above())) {
               BlockPos front0 = base.relative(face);
               BlockPos front1 = base.above().relative(face);
               if (isEmptyForPortal(world, front0) && isEmptyForPortal(world, front1)) {
                  Vec3 wallCenter = getWallPortalCenter(base, face);
                  if (isPortalBlocking(world, wallCenter, face, serverPlayer.getYRot(), serverPlayer, type)) {
                     playFailSound(serverPlayer);
                     return false;
                  } else {
                     PortalManager.placePortal(world, serverPlayer, base, face, type, serverPlayer.getYRot());
                     spawnShootParticles(world, serverPlayer, type);
                     return true;
                  }
               } else {
                  playFailSound(serverPlayer);
                  return false;
               }
            } else {
               playFailSound(serverPlayer);
               return false;
            }
         } else {
            float snappedYaw = snapYawTo90(serverPlayer.getYRot());
            Direction axis = Direction.fromYRot(snappedYaw);
            int dirY = face == Direction.UP ? 1 : -1;
            BlockPos a0 = hitPosBlock.relative(axis, 0).relative(Direction.UP, dirY);
            BlockPos a1 = hitPosBlock.relative(axis, 1).relative(Direction.UP, dirY);
            BlockPos b0 = a0.relative(Direction.UP, dirY);
            BlockPos b1 = a1.relative(Direction.UP, dirY);
            BlockPos otherSurface = getFloorOtherSurface(hitPosBlock, axis, hitPos);
            if (!isValidPortalSurface(world, hitPosBlock) || !isValidPortalSurface(world, otherSurface)) {
               playFailSound(serverPlayer);
               return false;
            }

            if (isEmptyForPortal(world, a0) && isEmptyForPortal(world, a1) && isEmptyForPortal(world, b0) && isEmptyForPortal(world, b1)) {
               Vec3 floorCenter = getFloorPortalCenter(hitPosBlock, face, axis, hitPos);
               if (isPortalBlocking(world, floorCenter, face, snappedYaw, serverPlayer, type)) {
                  playFailSound(serverPlayer);
                  return false;
               } else {
                  PortalManager.placePortalFloorCeiling(world, serverPlayer, hitPosBlock, face, type, snappedYaw, hitPos);
                  spawnShootParticles(world, serverPlayer, type);
                  return true;
               }
            } else {
               playFailSound(serverPlayer);
               return false;
            }
         }
      }
   }

   public static void markFiring(ServerPlayer player, ItemStack stack) {
      if (player != null && stack != null && stack.getItem() instanceof PortalGunItem) {
         setAnimTicks(stack, 12);
      }
   }

   public static void markClearAnimation(ItemStack stack) {
      if (stack != null && stack.getItem() instanceof PortalGunItem) {
         setClearAnimTicks(stack, 14);
      }
   }

   public void inventoryTick(ItemStack stack, ServerLevel world, Entity entity, EquipmentSlot slot) {
      super.inventoryTick(stack, world, entity, slot);
      int ticks = getAnimTicks(stack);
      if (ticks > 0) {
         setAnimTicks(stack, ticks - 1);
      }

      int clearTicks = getClearAnimTicks(stack);
      if (clearTicks > 0) {
         setClearAnimTicks(stack, clearTicks - 1);
      }
   }

   private static float snapYawTo90(float yaw) {
      float wrapped = Mth.wrapDegrees(yaw);
      return Math.round(wrapped / 90.0F) * 90.0F;
   }

   private static boolean isEmptyForPortal(Level world, BlockPos pos) {
      return world.getBlockState(pos).getCollisionShape(world, pos).isEmpty();
   }

   private static boolean isValidPortalSurface(Level world, BlockPos pos) {
      BlockState state = world.getBlockState(pos);
      return state.isCollisionShapeFullBlock(world, pos) && state.canOcclude();
   }

   private static boolean isMoonShot(ServerLevel world, ServerPlayer player) {
      if (world == null || player == null || world.dimension() != Level.OVERWORLD || !world.isDarkOutside()) {
         return false;
      } else {
         return player.getViewVector(1.0F).y < 0.55 ? false : world.canSeeSkyFromBelowWater(player.blockPosition().above());
      }
   }

   private static void playFailSound(ServerPlayer player) {
      if (player != null) {
         ServerLevel world = player.level();
         world.playSound(
            null,
            player.getX(),
            player.getY(),
            player.getZ(),
            BuiltInRegistries.SOUND_EVENT.wrapAsHolder(ModSounds.PORTAL_FAIL),
            SoundSource.PLAYERS,
            0.8F,
            1.0F
         );
      }
   }

   private static boolean isPortalBlocking(ServerLevel world, Vec3 center, Direction face, float yaw, ServerPlayer owner, PortalType type) {
      AABB check = PortalEntity.createPortalBox(center, face, yaw).inflate(0.05);
      Iterator var7 = world.getEntitiesOfClass(PortalEntity.class, check.inflate(2.0), e -> true).iterator();

      while (true) {
         if (!var7.hasNext()) {
            return false;
         }

         PortalEntity portal = (PortalEntity)var7.next();
         if (!portal.isRemoved() && portal.getTeleportBox().intersects(check)) {
            if (owner == null) {
               break;
            }

            UUID portalOwner = portal.getOwner();
            if (portalOwner == null || !portalOwner.equals(owner.getUUID()) || portal.getPortalType() != type) {
               break;
            }
         }
      }

      return true;
   }

   public static int getAnimTicks(ItemStack stack) {
      return getStoredTicks(stack, "portalgun_anim_ticks");
   }

   public static int getClearAnimTicks(ItemStack stack) {
      return getStoredTicks(stack, "portalgun_clear_anim_ticks");
   }

   public static boolean isGrabActive(ItemStack stack) {
      if (stack == null) {
         return false;
      }

      CustomData component = getCustomDataComponent(stack);
      return component == null ? false : component.copyTag().getBooleanOr("portalgun_grab_active", false);
   }

   private static int getStoredTicks(ItemStack stack, String key) {
      if (stack == null) {
         return 0;
      }

      CustomData component = getCustomDataComponent(stack);
      return component == null ? 0 : component.copyTag().getIntOr(key, 0);
   }

   public static void setAnimTicks(ItemStack stack, int ticks) {
      setStoredTicks(stack, "portalgun_anim_ticks", ticks);
      setFiringModelData(stack, ticks > 0);
   }

   public static void setClearAnimTicks(ItemStack stack, int ticks) {
      setStoredTicks(stack, "portalgun_clear_anim_ticks", ticks);
   }

   public static void setGrabActive(ItemStack stack, boolean active) {
      if (stack != null && stack.getItem() instanceof PortalGunItem) {
         CustomData component = getCustomDataComponent(stack);
         CompoundTag nbt = component == null ? new CompoundTag() : component.copyTag();
         if (active) {
            nbt.putBoolean("portalgun_grab_active", true);
         } else {
            nbt.remove("portalgun_grab_active");
         }

         setCustomDataComponent(stack, CustomData.of(nbt));
      }
   }

   private static void setStoredTicks(ItemStack stack, String key, int ticks) {
      if (stack != null) {
         CustomData component = getCustomDataComponent(stack);
         CompoundTag nbt = component == null ? new CompoundTag() : component.copyTag();
         if (ticks <= 0) {
            nbt.remove(key);
         } else {
            nbt.putInt(key, ticks);
         }

         setCustomDataComponent(stack, CustomData.of(nbt));
      }
   }

   private static CustomData getCustomDataComponent(ItemStack stack) {
      return (CustomData)stack.get(DataComponents.CUSTOM_DATA);
   }

   private static void setCustomDataComponent(ItemStack stack, CustomData component) {
      if (component == null) {
         stack.remove(DataComponents.CUSTOM_DATA);
      } else {
         if (component.copyTag().isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
         } else {
            stack.set(DataComponents.CUSTOM_DATA, component);
         }
      }
   }

   private static void setFiringModelData(ItemStack stack, boolean firing) {
      if (firing) {
         stack.set(DataComponents.CUSTOM_MODEL_DATA, FIRING_MODEL_DATA);
      } else {
         stack.remove(DataComponents.CUSTOM_MODEL_DATA);
      }
   }

   private static void spawnShootParticles(ServerLevel world, ServerPlayer player, PortalType type) {
      Vector3f color = type == PortalType.BLUE ? new Vector3f(0.3F, 0.6F, 1.0F) : new Vector3f(1.0F, 0.6F, 0.2F);
      ParticleOptions dust = PortalParticles.createDust(color, 1.0F);
      world.sendParticles(dust, player.getX(), player.getY() + 1.2, player.getZ(), 10, 0.15, 0.15, 0.15, 0.01);
   }

   private static Vec3 getWallPortalCenter(BlockPos base, Direction face) {
      double x = base.getX() + 0.5;
      double y = base.getY();
      double z = base.getZ() + 0.5;
      double offset = 0.503;
      x += face.getStepX() * offset;
      y += face.getStepY() * offset;
      z += face.getStepZ() * offset;
      return new Vec3(x, y, z);
   }

   private static Vec3 getFloorPortalCenter(BlockPos hitBlock, Direction face, Direction axis, Vec3 hitPos) {
      double localX = hitPos.x - hitBlock.getX();
      double localZ = hitPos.z - hitBlock.getZ();
      double centerX = hitBlock.getX() + 0.5;
      double centerZ = hitBlock.getZ() + 0.5;
      if (axis != Direction.EAST && axis != Direction.WEST) {
         boolean useSouth = localZ >= 0.5;
         centerZ = hitBlock.getZ() + (useSouth ? 1.0 : 0.0);
         centerX = hitBlock.getX() + 0.5;
      } else {
         boolean useEast = localX >= 0.5;
         centerX = hitBlock.getX() + (useEast ? 1.0 : 0.0);
         centerZ = hitBlock.getZ() + 0.5;
      }

      double eps = 0.003;
      double y = face == Direction.UP ? hitBlock.getY() + 1.0 + eps : hitBlock.getY() - eps;
      return new Vec3(centerX, y, centerZ);
   }

   private static BlockPos getFloorOtherSurface(BlockPos hitBlock, Direction axis, Vec3 hitPos) {
      double localX = hitPos.x - hitBlock.getX();
      double localZ = hitPos.z - hitBlock.getZ();
      if (axis != Direction.EAST && axis != Direction.WEST) {
         boolean useSouth = localZ >= 0.5;
         return useSouth ? hitBlock.south() : hitBlock.north();
      } else {
         boolean useEast = localX >= 0.5;
         return useEast ? hitBlock.east() : hitBlock.west();
      }
   }
}
