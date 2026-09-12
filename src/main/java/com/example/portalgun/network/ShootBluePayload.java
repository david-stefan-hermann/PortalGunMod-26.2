package com.example.portalgun.network;

import net.minecraft.resources.Identifier;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;

public record ShootBluePayload() implements CustomPacketPayload {
   public static final Type<ShootBluePayload> ID = new Type(Identifier.fromNamespaceAndPath("portalgun", "shoot_blue"));
   public static final StreamCodec<RegistryFriendlyByteBuf, ShootBluePayload> CODEC = StreamCodec.unit(new ShootBluePayload());

   public Type<? extends CustomPacketPayload> type() {
      return ID;
   }
}
