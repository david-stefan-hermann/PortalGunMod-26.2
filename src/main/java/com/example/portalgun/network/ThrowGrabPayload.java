package com.example.portalgun.network;

import net.minecraft.resources.Identifier;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;

public record ThrowGrabPayload() implements CustomPacketPayload {
   public static final Type<ThrowGrabPayload> ID = new Type(Identifier.fromNamespaceAndPath("portalgun", "throw_grab"));
   public static final StreamCodec<RegistryFriendlyByteBuf, ThrowGrabPayload> CODEC = StreamCodec.unit(new ThrowGrabPayload());

   public Type<? extends CustomPacketPayload> type() {
      return ID;
   }
}
