package com.example.portalgun.network;

import net.minecraft.resources.Identifier;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;

public record PortalViewPayload(int portalEntityId, int[] pixels) implements CustomPacketPayload {
   public static final int VIEW_COLUMNS = 72;
   public static final int VIEW_ROWS = 108;
   public static final int PIXEL_COUNT = 7776;
   public static final Type<PortalViewPayload> ID = new Type(Identifier.fromNamespaceAndPath("portalgun", "portal_view"));
   public static final StreamCodec<RegistryFriendlyByteBuf, PortalViewPayload> CODEC = StreamCodec.ofMember(PortalViewPayload::write, PortalViewPayload::new);

   public PortalViewPayload {
      if (pixels.length != 7776) {
         throw new IllegalArgumentException("Portal view payload must contain 7776 pixels");
      }

      pixels = (int[])pixels.clone();
   }

   private PortalViewPayload(RegistryFriendlyByteBuf buf) {
      this(buf.readVarInt(), readPixels(buf));
   }

   private void write(RegistryFriendlyByteBuf buf) {
      buf.writeVarInt(this.portalEntityId);
      buf.writeVarInt(this.pixels.length);

      for (int pixel : this.pixels) {
         buf.writeInt(pixel);
      }
   }

   private static int[] readPixels(RegistryFriendlyByteBuf buf) {
      int length = buf.readVarInt();
      if (length != 7776) {
         throw new IllegalArgumentException("Portal view payload had " + length + " pixels");
      }

      int[] pixels = new int[length];

      for (int i = 0; i < pixels.length; i++) {
         pixels[i] = buf.readInt();
      }

      return pixels;
   }

   public int[] pixels() {
      return (int[])this.pixels.clone();
   }

   public Type<? extends CustomPacketPayload> type() {
      return ID;
   }
}
