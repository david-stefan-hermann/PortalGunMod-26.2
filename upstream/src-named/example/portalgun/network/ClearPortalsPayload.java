package com.example.portalgun.network;

import net.minecraft.resources.Identifier;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;

public record ClearPortalsPayload() implements CustomPacketPayload {
   public static final Type<ClearPortalsPayload> ID = new Type(Identifier.fromNamespaceAndPath("portalgun", "clear_portals"));
   public static final StreamCodec<RegistryFriendlyByteBuf, ClearPortalsPayload> CODEC = StreamCodec.unit(new ClearPortalsPayload());

   public Type<? extends CustomPacketPayload> type() {
      return ID;
   }
}
