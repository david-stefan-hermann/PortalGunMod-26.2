package com.example.portalgun.network;

import net.minecraft.resources.Identifier;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;

public record PortalStatusPayload(boolean hasBlue, boolean hasOrange) implements CustomPacketPayload {
   public static final Type<PortalStatusPayload> ID = new Type(Identifier.fromNamespaceAndPath("portalgun", "portal_status"));
   public static final StreamCodec<RegistryFriendlyByteBuf, PortalStatusPayload> CODEC = StreamCodec.composite(
      ByteBufCodecs.BOOL, PortalStatusPayload::hasBlue, ByteBufCodecs.BOOL, PortalStatusPayload::hasOrange, PortalStatusPayload::new
   );

   public PortalStatusPayload {
   }

   public Type<? extends CustomPacketPayload> type() {
      return ID;
   }
}
