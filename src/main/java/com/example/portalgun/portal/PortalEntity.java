package com.example.portalgun.portal;

import com.example.portalgun.ModSounds;
import com.example.portalgun.util.PortalParticles;
import java.util.UUID;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.network.syncher.SynchedEntityData.Builder;
import org.joml.Vector3f;

public class PortalEntity extends Entity {
   private static final double WALL_HALF_WIDTH = 0.55;
   private static final double WALL_HEIGHT = 2.0;
   private static final double HORIZONTAL_HALF_WIDTH = 0.55;
   private static final double HORIZONTAL_HALF_LENGTH = 1.05;
   private static final double PLANE_HALF_THICKNESS = 0.12;
   private static final EntityDataAccessor<Integer> PORTAL_TYPE = SynchedEntityData.defineId(PortalEntity.class, EntityDataSerializers.INT);
   private static final EntityDataAccessor<String> FACING = SynchedEntityData.defineId(PortalEntity.class, EntityDataSerializers.STRING);
   private static final EntityDataAccessor<Boolean> BLACK_HOLE = SynchedEntityData.defineId(PortalEntity.class, EntityDataSerializers.BOOLEAN);
   private static final EntityDataAccessor<String> OWNER = SynchedEntityData.defineId(PortalEntity.class, EntityDataSerializers.STRING);
   private PortalEntity linked;
   private BlockPos backingA;
   private BlockPos backingB;
   private UUID owner;

   public PortalEntity(EntityType<? extends PortalEntity> type, Level world) {
      super(type, world);
      this.noPhysics = true;
   }

   public boolean shouldBeSaved() {
      return false;
   }

   public boolean hurtServer(ServerLevel world, DamageSource source, float amount) {
      return false;
   }

   public void onRemoval(RemovalReason reason) {
      super.onRemoval(reason);
      if (!this.level().isClientSide()) {
         PortalManager.onPortalRemoved(this);
      }
   }

   protected void defineSynchedData(Builder builder) {
      builder.define(PORTAL_TYPE, PortalType.BLUE.ordinal());
      builder.define(FACING, Direction.SOUTH.getName());
      builder.define(BLACK_HOLE, false);
      builder.define(OWNER, "");
   }

   public void tick() {
      super.tick();
      Level world = this.level();
      if (!world.isClientSide()) {
         if (!this.hasValidBacking()) {
            world.playSound(
               null,
               this.getX(),
               this.getY(),
               this.getZ(),
               BuiltInRegistries.SOUND_EVENT.wrapAsHolder(ModSounds.PORTAL_FIZZLE),
               SoundSource.PLAYERS,
               0.8F,
               1.0F
            );
            if (world instanceof ServerLevel serverWorld) {
               Vector3f color = this.getPortalType() == PortalType.ORANGE ? new Vector3f(1.0F, 0.65F, 0.25F) : new Vector3f(0.3F, 0.65F, 1.0F);
               ParticleOptions dust = PortalParticles.createDust(color, 1.0F);
               serverWorld.sendParticles(dust, this.getX(), this.getY() + 0.9, this.getZ(), 20, 0.25, 0.5, 0.25, 0.02);
            }

            this.discard();
         } else if (this.isBlackHole()) {
            PortalManager.tickBlackHole(this);
         } else if (this.linked != null && !this.linked.isRemoved()) {
            AABB box = this.getTeleportBox().inflate(0.05);

            for (Entity e : world.getEntities(this, box)) {
               if (e.isAlive() && !(e instanceof PortalEntity) && !e.isOnPortalCooldown()) {
                  PortalManager.teleport(e, this.linked);
               }
            }
         }
      }
   }

   public boolean canBeHitByProjectile() {
      return false;
   }

   public boolean isPickable() {
      return false;
   }

   public boolean canBeCollidedWith(Entity other) {
      return false;
   }

   public boolean canCollideWith(Entity other) {
      return false;
   }

   public void setPortalType(PortalType type) {
      this.entityData.set(PORTAL_TYPE, type.ordinal());
   }

   public PortalType getPortalType() {
      int i = (Integer)this.entityData.get(PORTAL_TYPE);
      PortalType[] vals = PortalType.values();
      return i >= 0 && i < vals.length ? vals[i] : PortalType.BLUE;
   }

   public void setBlackHole(boolean blackHole) {
      this.entityData.set(BLACK_HOLE, blackHole);
   }

   public boolean isBlackHole() {
      return (Boolean)this.entityData.get(BLACK_HOLE);
   }

   public void setFacing(Direction dir) {
      this.entityData.set(FACING, dir.getName());
   }

   public Direction getNearestViewDirection() {
      Direction dir = Direction.byName((String)this.entityData.get(FACING));
      return dir == null ? Direction.SOUTH : dir;
   }

   public void setOwner(UUID owner) {
      this.owner = owner;
      this.entityData.set(OWNER, owner == null ? "" : owner.toString());
   }

   public UUID getOwner() {
      String trackedOwner = (String)this.entityData.get(OWNER);
      if (!trackedOwner.isEmpty()) {
         try {
            return UUID.fromString(trackedOwner);
         } catch (Exception var3) {
         }
      }

      return this.owner;
   }

   public void setLinked(PortalEntity other) {
      this.linked = other;
   }

   public PortalEntity getLinked() {
      return this.linked;
   }

   public void setBacking(BlockPos a, BlockPos b) {
      this.backingA = a;
      this.backingB = b;
   }

   public boolean isBackingBlock(BlockPos pos) {
      return pos != null && (pos.equals(this.backingA) || pos.equals(this.backingB));
   }

   public AABB getTeleportBox() {
      return createPortalBox(new Vec3(this.getX(), this.getY(), this.getZ()), this.getNearestViewDirection(), this.getYRot());
   }

   public static AABB createPortalBox(Vec3 center, Direction face, float yaw) {
      Direction safeFace = face == null ? Direction.SOUTH : face;
      if (safeFace != Direction.UP && safeFace != Direction.DOWN) {
         return switch (safeFace) {
            case NORTH, SOUTH -> new AABB(
               center.x - 0.55,
               center.y,
               center.z - 0.12,
               center.x + 0.55,
               center.y + 2.0,
               center.z + 0.12
            );
            case EAST, WEST -> new AABB(
               center.x - 0.12,
               center.y,
               center.z - 0.55,
               center.x + 0.12,
               center.y + 2.0,
               center.z + 0.55
            );
            default -> new AABB(
               center.x - 0.55,
               center.y,
               center.z - 0.12,
               center.x + 0.55,
               center.y + 2.0,
               center.z + 0.12
            );
         };
      } else {
         boolean lengthAlongX = isHorizontalLengthAlongX(yaw);
         double halfX = lengthAlongX ? 1.05 : 0.55;
         double halfZ = lengthAlongX ? 0.55 : 1.05;
         return new AABB(
            center.x - halfX,
            center.y - 0.12,
            center.z - halfZ,
            center.x + halfX,
            center.y + 0.12,
            center.z + halfZ
         );
      }
   }

   private static boolean isHorizontalLengthAlongX(float yaw) {
      float wrapped = Math.abs(Mth.wrapDegrees(yaw));
      return Math.abs(wrapped - 90.0F) < 45.0F;
   }

   protected void readAdditionalSaveData(ValueInput view) {
      PortalType type = PortalType.BLUE;
      String typeName = view.getStringOr("PortalType", "");
      if (!typeName.isEmpty()) {
         try {
            type = PortalType.valueOf(typeName);
         } catch (Exception var9) {
         }
      }

      this.setPortalType(type);
      this.setBlackHole(view.getBooleanOr("BlackHole", false));
      String facingId = view.getStringOr("Facing", Direction.SOUTH.getName());
      Direction facing = Direction.byName(facingId);
      this.setFacing(facing == null ? Direction.SOUTH : facing);
      String ownerId = view.getStringOr("Owner", "");
      if (!ownerId.isEmpty()) {
         try {
            this.setOwner(UUID.fromString(ownerId));
         } catch (Exception var8) {
         }
      }

      view.getLong("BackingA").ifPresent(value -> this.backingA = BlockPos.of(value));
      view.getLong("BackingB").ifPresent(value -> this.backingB = BlockPos.of(value));
   }

   protected void addAdditionalSaveData(ValueOutput view) {
      view.putString("PortalType", this.getPortalType().name());
      view.putBoolean("BlackHole", this.isBlackHole());
      view.putString("Facing", this.getNearestViewDirection().getName());
      UUID owner = this.getOwner();
      if (owner != null) {
         view.putString("Owner", owner.toString());
      }

      if (this.backingA != null) {
         view.putLong("BackingA", this.backingA.asLong());
      }

      if (this.backingB != null) {
         view.putLong("BackingB", this.backingB.asLong());
      }
   }

   private boolean hasValidBacking() {
      return this.backingA != null && this.backingB != null ? this.isSolidBacking(this.backingA) && this.isSolidBacking(this.backingB) : true;
   }

   private boolean isSolidBacking(BlockPos pos) {
      Level world = this.level();
      BlockState state = world.getBlockState(pos);
      return state.isCollisionShapeFullBlock(world, pos) && state.canOcclude();
   }
}
