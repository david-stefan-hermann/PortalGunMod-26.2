package com.example.portalgun.client;

import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.Direction;

public class PortalGunPedestalBlockEntityRenderState extends BlockEntityRenderState {
   public ItemStackRenderState portalGunRenderState;
   public Direction facing = Direction.NORTH;

   public PortalGunPedestalBlockEntityRenderState() {
   }
}
