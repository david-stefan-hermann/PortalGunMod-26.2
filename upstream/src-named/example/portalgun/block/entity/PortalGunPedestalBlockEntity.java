package com.example.portalgun.block.entity;

import com.example.portalgun.PortalGunMod;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.HolderLookup.Provider;

public class PortalGunPedestalBlockEntity extends BlockEntity implements ItemOwner {
   private static final String PORTAL_GUN_KEY = "PortalGun";
   private ItemStack portalGun = ItemStack.EMPTY;

   public PortalGunPedestalBlockEntity(BlockPos pos, BlockState state) {
      super(PortalGunMod.PORTAL_GUN_PEDESTAL_BLOCK_ENTITY, pos, state);
   }

   public ItemStack getPortalGun() {
      return this.portalGun;
   }

   public boolean isEmpty() {
      return this.portalGun.isEmpty();
   }

   public void setPortalGun(ItemStack stack) {
      this.portalGun = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
      this.sync();
   }

   public ItemStack removePortalGun() {
      ItemStack stack = this.removePortalGunWithoutSync();
      this.sync();
      return stack;
   }

   public ItemStack removePortalGunWithoutSync() {
      ItemStack stack = this.portalGun;
      this.portalGun = ItemStack.EMPTY;
      return stack;
   }

   protected void loadAdditional(ValueInput view) {
      super.loadAdditional(view);
      this.portalGun = view.read("PortalGun", ItemStack.CODEC).orElse(ItemStack.EMPTY);
      if (!this.portalGun.isEmpty()) {
         this.portalGun.setCount(1);
      }
   }

   protected void saveAdditional(ValueOutput view) {
      super.saveAdditional(view);
      if (!this.portalGun.isEmpty()) {
         view.store("PortalGun", ItemStack.CODEC, this.portalGun);
      }
   }

   public Packet<ClientGamePacketListener> getUpdatePacket() {
      return ClientboundBlockEntityDataPacket.create(this);
   }

   public CompoundTag getUpdateTag(Provider registries) {
      return this.saveWithoutMetadata(registries);
   }

   public Level level() {
      return this.level;
   }

   public Vec3 position() {
      return Vec3.atCenterOf(this.worldPosition);
   }

   public float getVisualRotationYInDegrees() {
      return 0.0F;
   }

   private void sync() {
      this.setChanged();
      if (this.level != null) {
         this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
      }
   }
}
