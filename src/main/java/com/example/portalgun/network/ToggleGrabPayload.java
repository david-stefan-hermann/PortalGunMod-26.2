package com.example.portalgun.network;

import net.minecraft.resources.Identifier;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;

public record ToggleGrabPayload() implements CustomPacketPayload {
   public static final Type<ToggleGrabPayload> ID = new Type(Identifier.fromNamespaceAndPath("portalgun", "toggle_grab"));
   public static final StreamCodec<RegistryFriendlyByteBuf, ToggleGrabPayload> CODEC = StreamCodec.unit(new ToggleGrabPayload());

   public Type<? extends CustomPacketPayload> type() {
      return ID;
   }
}
