package com.example.portalgun.network;

import net.minecraft.resources.Identifier;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;

public record GrabStatusPayload(boolean active) implements CustomPacketPayload {
   public static final Type<GrabStatusPayload> ID = new Type(Identifier.fromNamespaceAndPath("portalgun", "grab_status"));
   public static final StreamCodec<RegistryFriendlyByteBuf, GrabStatusPayload> CODEC = StreamCodec.composite(
      ByteBufCodecs.BOOL, GrabStatusPayload::active, GrabStatusPayload::new
   );

   public GrabStatusPayload {
   }

   public Type<? extends CustomPacketPayload> type() {
      return ID;
   }
}
