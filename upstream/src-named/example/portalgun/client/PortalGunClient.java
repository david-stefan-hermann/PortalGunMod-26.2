package com.example.portalgun.client;

import com.example.portalgun.ModEntities;
import com.example.portalgun.ModSounds;
import com.example.portalgun.PortalGunMod;
import com.example.portalgun.item.PortalGunItem;
import com.example.portalgun.network.ClearPortalsPayload;
import com.example.portalgun.network.GrabStatusPayload;
import com.example.portalgun.network.PortalStatusPayload;
import com.example.portalgun.network.PortalViewPayload;
import com.example.portalgun.network.ShootBluePayload;
import com.example.portalgun.network.ThrowGrabPayload;
import com.example.portalgun.network.ToggleGrabPayload;
import com.example.portalgun.util.PortalParticles;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.renderer.special.SpecialModelRenderers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.KeyMapping.Category;
import com.mojang.blaze3d.platform.InputConstants.Type;
import org.joml.Vector3f;

public class PortalGunClient implements ClientModInitializer {
   private static KeyMapping CLEAR_KEY;
   private static KeyMapping GRAB_KEY;
   private static final Category PORTAL_GUN_CATEGORY = Category.register(Identifier.fromNamespaceAndPath("portalgun", "portalgun"));
   private static int blueCooldownTicks = 0;
   private static int orangeCooldownTicks = 0;
   private static int throwCooldownTicks = 0;
   private static boolean holdingInit = false;
   private static boolean lastHoldingGun = false;
   private static boolean hasBluePortal = false;
   private static boolean hasOrangePortal = false;
   private static boolean hasGrabbedEntity = false;
   private static PortalGunHoldLoopSound holdLoopSound = null;

   public PortalGunClient() {
   }

   public void onInitializeClient() {
      SpecialModelRenderers.ID_MAPPER.put(Identifier.fromNamespaceAndPath("portalgun", "portal_gun"), PortalGunItemRenderer.Unbaked.CODEC);
      ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
         public Identifier getFabricId() {
            return Identifier.fromNamespaceAndPath("portalgun", "portal_gun_obj_model");
         }

         public void onResourceManagerReload(ResourceManager manager) {
            PortalGunObjModel.reload(manager);
         }
      });
      EntityRenderers.register(ModEntities.PORTAL, PortalRenderer::new);
      BlockEntityRendererRegistry.register(PortalGunMod.PORTAL_GUN_PEDESTAL_BLOCK_ENTITY, PortalGunPedestalBlockEntityRenderer::new);
      CLEAR_KEY = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.portalgun.clear_portals", Type.KEYSYM, 86, PORTAL_GUN_CATEGORY));
      GRAB_KEY = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.portalgun.grab_entity", Type.KEYSYM, 71, PORTAL_GUN_CATEGORY));
      ClientTickEvents.END_CLIENT_TICK
         .register(
            (EndTick)client -> {
               if (client.player == null) {
                  stopGrabHoldLoop(client);
               } else {
                  boolean isHoldingGun = client.player.getMainHandItem().getItem() == PortalGunMod.PORTAL_GUN;
                  if (!holdingInit) {
                     lastHoldingGun = isHoldingGun;
                     holdingInit = true;
                  } else if (!lastHoldingGun && isHoldingGun) {
                     client.player.playSound(ModSounds.PORTAL_GUN_ACTIVATE, 0.2F, 1.0F);
                  }

                  lastHoldingGun = isHoldingGun;
                  if (blueCooldownTicks > 0) {
                     blueCooldownTicks--;
                  }

                  if (orangeCooldownTicks > 0) {
                     orangeCooldownTicks--;
                  }

                  if (throwCooldownTicks > 0) {
                     throwCooldownTicks--;
                  }

                  if (client.player.getMainHandItem().getItem() == PortalGunMod.PORTAL_GUN && client.options.keyAttack.isDown()) {
                     client.options.keyAttack.setDown(false);
                     if (client.gameMode != null) {
                        client.gameMode.stopDestroyBlock();
                     }

                     client.player.swinging = false;
                     client.player.swingTime = 0;
                     client.player.attackAnim = 0.0F;
                     client.player.oAttackAnim = 0.0F;
                     if (hasGrabbedEntity) {
                        throwGrabbedEntity(client);
                     } else if (blueCooldownTicks == 0) {
                        ClientPlayNetworking.send(new ShootBluePayload());
                        markClientFiring(client, true);
                        blueCooldownTicks = 4;
                     }
                  }

                  if (client.player.getMainHandItem().getItem() == PortalGunMod.PORTAL_GUN && client.options.keyUse.isDown()) {
                     client.player.swinging = false;
                     client.player.swingTime = 0;
                     client.player.attackAnim = 0.0F;
                     client.player.oAttackAnim = 0.0F;
                     client.player.releaseUsingItem();
                     if (hasGrabbedEntity) {
                        throwGrabbedEntity(client);
                        client.options.keyUse.setDown(false);
                     } else if (orangeCooldownTicks == 0) {
                        markClientFiring(client, false);
                        orangeCooldownTicks = 6;
                     }
                  }

                  while (CLEAR_KEY.consumeClick()) {
                     if (client.player.getMainHandItem().getItem() != PortalGunMod.PORTAL_GUN
                        && client.player.getOffhandItem().getItem() != PortalGunMod.PORTAL_GUN) {
                        client.player.displayClientMessage(Component.literal("Hold the Portal Gun to clear portals."), true);
                     } else {
                        ClientPlayNetworking.send(new ClearPortalsPayload());
                        markClientClear(client);
                        client.player.displayClientMessage(Component.literal("Clearing portals..."), true);
                     }
                  }

                  while (GRAB_KEY.consumeClick()) {
                     if (client.player.getMainHandItem().getItem() != PortalGunMod.PORTAL_GUN) {
                        client.player.displayClientMessage(Component.literal("Hold the Portal Gun to grab mobs."), true);
                     } else {
                        ClientPlayNetworking.send(new ToggleGrabPayload());
                     }
                  }

                  updateGrabHoldLoop(client);
               }
            }
         );
      ClientPlayNetworking.registerGlobalReceiver(PortalStatusPayload.ID, (payload, context) -> context.client().execute(() -> {
         hasBluePortal = payload.hasBlue();
         hasOrangePortal = payload.hasOrange();
      }));
      ClientPlayNetworking.registerGlobalReceiver(
         GrabStatusPayload.ID, (payload, context) -> context.client().execute(() -> setClientGrabActive(context.client(), payload.active()))
      );
      ClientPlayNetworking.registerGlobalReceiver(
         PortalViewPayload.ID,
         (payload, context) -> context.client().execute(() -> PortalRenderer.acceptRemoteViewSnapshot(payload.portalEntityId(), payload.pixels()))
      );
      HudElementRegistry.attachElementAfter(
         VanillaHudElements.CROSSHAIR, Identifier.fromNamespaceAndPath("portalgun", "portal_reticle"), (drawContext, tickCounter) -> {
            if (Minecraft.getInstance().player != null) {
               if (Minecraft.getInstance().player.getMainHandItem().getItem() == PortalGunMod.PORTAL_GUN) {
                  int w = drawContext.guiWidth();
                  int h = drawContext.guiHeight();
                  float cx = w / 2.0F - 1.0F;
                  float cy = h / 2.0F - 1.5F;
                  int blue = hasBluePortal ? -11557889 : 1079676159;
                  int orange = hasOrangePortal ? -23744 : 1090495296;
                  drawPortalBracket(drawContext, cx, cy, -1.0F, blue, hasBluePortal);
                  drawPortalBracket(drawContext, cx, cy, 1.0F, orange, hasOrangePortal);
               }
            }
         }
      );
   }

   private static void drawPortalBracket(GuiGraphics ctx, float cx, float cy, float side, int color, boolean active) {
      int core = active ? color : withAlpha(color, 95);
      int inner = active ? withAlpha(color, 145) : withAlpha(color, 45);
      int glow = active ? withAlpha(color, 70) : withAlpha(color, 24);
      float radius = 9.5F;
      int steps = 28;

      for (int i = 0; i <= steps; i++) {
         float angle = (float)Math.toRadians(-54.0F + 108.0F * ((float)i / steps));
         float cos = (float)Math.cos(angle);
         float sin = (float)Math.sin(angle);
         int x = Math.round(cx + side * cos * radius);
         int y = Math.round(cy + sin * 6.9F);
         drawReticlePixel(ctx, x + Math.round(side), y, glow);
         drawReticlePixel(ctx, x, y - 1, inner);
         drawReticlePixel(ctx, x, y + 1, inner);
         drawReticlePixel(ctx, x, y, core);
      }
   }

   private static void drawReticlePixel(GuiGraphics ctx, int x, int y, int color) {
      ctx.fill(x, y, x + 1, y + 1, color);
   }

   private static int withAlpha(int color, int alpha) {
      int clampedAlpha = Math.max(0, Math.min(255, alpha));
      return clampedAlpha << 24 | color & 16777215;
   }

   private static void markClientFiring(Minecraft client, boolean isBlue) {
      if (client != null && client.player != null) {
         ItemStack stack = client.player.getMainHandItem();
         if (stack.getItem() == PortalGunMod.PORTAL_GUN) {
            PortalGunItem.setAnimTicks(stack, 12);
            if (client.level != null) {
               Vector3f color = isBlue ? new Vector3f(0.3F, 0.6F, 1.0F) : new Vector3f(1.0F, 0.6F, 0.2F);
               ParticleOptions dust = PortalParticles.createDust(color, 1.0F);
               Vec3 forward = client.player.getViewVector(1.0F);
               double x = client.player.getX() + forward.x * 0.6;
               double y = client.player.getY() + client.player.getEyeHeight(client.player.getPose()) - 0.2 + forward.y * 0.4;
               double z = client.player.getZ() + forward.z * 0.6;

               for (int i = 0; i < 8; i++) {
                  double dx = (client.level.random.nextDouble() - 0.5) * 0.06;
                  double dy = (client.level.random.nextDouble() - 0.5) * 0.06;
                  double dz = (client.level.random.nextDouble() - 0.5) * 0.06;
                  client.level.addParticle(dust, x, y, z, dx, dy, dz);
               }
            }
         }
      }
   }

   private static void throwGrabbedEntity(Minecraft client) {
      if (client != null && client.player != null && throwCooldownTicks <= 0) {
         ClientPlayNetworking.send(new ThrowGrabPayload());
         throwCooldownTicks = 6;
         blueCooldownTicks = Math.max(blueCooldownTicks, 4);
         orangeCooldownTicks = Math.max(orangeCooldownTicks, 4);
      }
   }

   private static void markClientClear(Minecraft client) {
      if (client != null && client.player != null) {
         ItemStack mainHandStack = client.player.getMainHandItem();
         if (mainHandStack.getItem() == PortalGunMod.PORTAL_GUN) {
            PortalGunItem.markClearAnimation(mainHandStack);
         } else {
            ItemStack offHandStack = client.player.getOffhandItem();
            if (offHandStack.getItem() == PortalGunMod.PORTAL_GUN) {
               PortalGunItem.markClearAnimation(offHandStack);
            }
         }
      }
   }

   private static void setClientGrabActive(Minecraft client, boolean active) {
      hasGrabbedEntity = active;
      if (client != null && client.player != null) {
         ItemStack mainHandStack = client.player.getMainHandItem();
         ItemStack offHandStack = client.player.getOffhandItem();
         PortalGunItem.setGrabActive(mainHandStack, active && mainHandStack.getItem() == PortalGunMod.PORTAL_GUN);
         PortalGunItem.setGrabActive(offHandStack, false);
         updateGrabHoldLoop(client);
      }
   }

   private static void updateGrabHoldLoop(Minecraft client) {
      if (client != null && client.player != null && hasGrabbedEntity && client.player.getMainHandItem().getItem() == PortalGunMod.PORTAL_GUN) {
         if (holdLoopSound == null || holdLoopSound.isStopped()) {
            holdLoopSound = new PortalGunHoldLoopSound(client, () -> hasGrabbedEntity);
            client.getSoundManager().play(holdLoopSound);
         }
      } else {
         stopGrabHoldLoop(client);
      }
   }

   private static void stopGrabHoldLoop(Minecraft client) {
      if (holdLoopSound != null) {
         holdLoopSound.stopLoop();
         if (client != null) {
            client.getSoundManager().stop(holdLoopSound);
         }

         holdLoopSound = null;
      }
   }
}
