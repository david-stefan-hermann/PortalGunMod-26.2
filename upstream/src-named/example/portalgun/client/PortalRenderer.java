package com.example.portalgun.client;

import com.example.portalgun.portal.PortalEntity;
import com.example.portalgun.portal.PortalType;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.resources.Identifier;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.multiplayer.ClientLevel;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.ClipContext.Block;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;

public class PortalRenderer extends EntityRenderer<PortalEntity, PortalRenderer.PortalRenderState> {
   private static final Identifier BLUE_OVAL = Identifier.fromNamespaceAndPath("portalgun", "textures/entity/portal_blue.png");
   private static final Identifier ORANGE_OVAL = Identifier.fromNamespaceAndPath("portalgun", "textures/entity/portal_orange.png");
   private static final Identifier WHITE_PIXEL = Identifier.fromNamespaceAndPath("portalgun", "textures/item/portalgun_white.png");
   private static final int VIEW_COLUMNS = 72;
   private static final int VIEW_ROWS = 108;
   private static final double VIEW_DISTANCE = 48.0;
   private static final double VIEW_SURFACE_OFFSET = 0.08;
   private static final double STATIONARY_CAMERA_DISTANCE = 1.25;
   private static final double PORTAL_VIEW_WIDTH = 1.0;
   private static final double PORTAL_VIEW_HEIGHT = 2.0;
   private static final int VIEW_REFRESH_TICKS = 40;
   private static final int CACHE_KEEPALIVE_TICKS = 200;
   private static final float VIEW_PLANE_OFFSET = 0.006F;
   private static final float RING_PLANE_OFFSET = 0.026F;
   private static final float OVAL_OPENING_HALF_WIDTH = 0.37F;
   private static final float OVAL_OPENING_HALF_HEIGHT = 0.87F;
   private static final int[] EMPTY_VIEW = new int[0];
   private static final Map<Integer, PortalRenderer.PortalViewCache> VIEW_CACHE = new HashMap<>();
   private static final Map<Integer, int[]> REMOTE_VIEW_SNAPSHOTS = new HashMap<>();

   public PortalRenderer(Context ctx) {
      super(ctx);
   }

   public PortalRenderer.PortalRenderState createRenderState() {
      return new PortalRenderer.PortalRenderState();
   }

   public static void acceptRemoteViewSnapshot(int portalEntityId, int[] pixels) {
      if (pixels.length == 7776) {
         REMOTE_VIEW_SNAPSHOTS.put(portalEntityId, (int[])pixels.clone());
      }
   }

   public boolean shouldRender(PortalEntity entity, Frustum frustum, double x, double y, double z) {
      return super.shouldRender(entity, frustum, x, y, z) || frustum.isVisible(entity.getBoundingBox().inflate(1.25));
   }

   public void updateRenderState(PortalEntity entity, PortalRenderer.PortalRenderState state, float tickDelta) {
      super.extractRenderState(entity, state, tickDelta);
      state.portalType = entity.getPortalType();
      state.portalEntityId = entity.getId();
      Direction face = entity.getNearestViewDirection();
      state.facing = face == null ? Direction.SOUTH : face;
      state.yaw = entity.getViewYRot(tickDelta);
      state.sourceCenter = portalCenter(entity);
      state.sourceBasis = basisForPortal(entity);
      PortalEntity linked = entity.isBlackHole() ? null : findLinkedPortal(entity);
      state.hasLinkedPortal = linked != null;
      if (linked != null) {
         int linkedEntityId = linked.getId();
         if (state.linkedEntityId != linkedEntityId) {
            state.linkedViewPixels = EMPTY_VIEW;
         }

         state.linkedEntityId = linkedEntityId;
         state.linkedCenter = portalCenter(linked);
         state.linkedBasis = basisForPortal(linked);
      } else {
         state.linkedEntityId = -1;
         state.linkedCenter = null;
         state.linkedBasis = null;
      }
   }

   public void render(PortalRenderer.PortalRenderState state, PoseStack matrices, SubmitNodeCollector renderQueue, CameraRenderState camera) {
      matrices.pushPose();
      Direction face = state.facing == null ? Direction.SOUTH : state.facing;
      state.linkedViewPixels = getStationaryLinkedViewPixels(state);
      RenderType viewLayer = RenderTypes.entityTranslucentEmissive(WHITE_PIXEL);
      RenderType ringLayer = RenderTypes.entityCutoutNoCull(getRingTexture(state));
      int light = state.lightCoords;
      if (face != Direction.UP && face != Direction.DOWN) {
         renderWallLinkedView(state, matrices, renderQueue, viewLayer, face);
         renderWallRing(matrices, renderQueue, ringLayer, light, face);
         matrices.popPose();
         super.submit(state, matrices, renderQueue, camera);
      } else {
         matrices.mulPose(Axis.YP.rotationDegrees(-state.yaw));
         matrices.translate(0.0, face == Direction.UP ? 0.001 : -0.001, 0.0);
         renderHorizontalLinkedView(state, matrices, renderQueue, viewLayer, nyForHorizontal(face));
         renderHorizontalRing(matrices, renderQueue, ringLayer, light, nyForHorizontal(face));
         matrices.popPose();
         super.submit(state, matrices, renderQueue, camera);
      }
   }

   private static int[] getStationaryLinkedViewPixels(PortalRenderer.PortalRenderState state) {
      ClientLevel world = Minecraft.getInstance().level;
      if (world != null && state.sourceCenter != null && state.sourceBasis != null && state.linkedCenter != null && state.linkedBasis != null) {
         long time = world.getGameTime();
         pruneViewCache(time);
         PortalRenderer.PortalViewCache cache = VIEW_CACHE.get(state.portalEntityId);
         if (cache != null
            && cache.linkedEntityId == state.linkedEntityId
            && cache.linkedCenter.distanceToSqr(state.linkedCenter) < 1.0E-6
            && time < cache.nextRefreshTick) {
            cache.lastUseTick = time;
            return cache.pixels;
         }

         int[] pixels = new int[7776];
         Vec3 stationaryCamera = state.linkedCenter.subtract(state.linkedBasis.forward.scale(1.25));

         for (int row = 0; row < 108; row++) {
            for (int col = 0; col < 72; col++) {
               int index = row * 72 + col;
               double x = ((col + 0.5) / 72.0 - 0.5) * 1.0;
               double y = (0.5 - (row + 0.5) / 108.0) * 2.0;
               if (!insideInsetOvalCenter(x, y)) {
                  pixels[index] = 0;
               } else {
                  Vec3 linkedSurfacePoint = state.linkedCenter
                     .add(state.linkedBasis.right.scale(x))
                     .add(state.linkedBasis.up.scale(y));
                  Vec3 ray = linkedSurfacePoint.subtract(stationaryCamera);
                  if (ray.lengthSqr() < 1.0E-6) {
                     ray = state.linkedBasis.forward;
                  } else {
                     ray = ray.normalize();
                  }

                  Vec3 rayStart = linkedSurfacePoint.add(ray.scale(0.08));
                  pixels[index] = sampleViewColor(world, rayStart, ray);
               }
            }
         }

         VIEW_CACHE.put(state.portalEntityId, new PortalRenderer.PortalViewCache(state.linkedEntityId, state.linkedCenter, pixels, time + 40L, time));
         return pixels;
      } else {
         int[] remotePixels = REMOTE_VIEW_SNAPSHOTS.get(state.portalEntityId);
         if (remotePixels != null) {
            return remotePixels;
         }

         if (state.linkedViewPixels.length == 0) {
            state.linkedViewPixels = createFallbackViewPixels(state);
         }

         return state.linkedViewPixels;
      }
   }

   private static void pruneViewCache(long time) {
      if (VIEW_CACHE.size() > 32) {
         VIEW_CACHE.entrySet().removeIf(entry -> time - entry.getValue().lastUseTick > 200L);
      }
   }

   private static int[] createFallbackViewPixels(PortalRenderer.PortalRenderState state) {
      int[] pixels = new int[7776];
      int top = state.portalType == PortalType.ORANGE ? -14610173 : -16575711;
      int bottom = state.portalType == PortalType.ORANGE ? -10540538 : -16370337;

      for (int row = 0; row < 108; row++) {
         double amount = (double)row / Math.max(1, 107);
         int color = blendColor(top, bottom, amount);

         for (int col = 0; col < 72; col++) {
            pixels[row * 72 + col] = color;
         }
      }

      return pixels;
   }

   private static int sampleViewColor(ClientLevel world, Vec3 start, Vec3 ray) {
      Vec3 end = start.add(ray.scale(48.0));
      BlockHitResult hit = world.clip(new ClipContext(start, end, Block.OUTLINE, Fluid.ANY, CollisionContext.empty()));
      if (hit.getType() != Type.BLOCK) {
         return skyColor(world, ray);
      }

      BlockPos pos = hit.getBlockPos();
      BlockState blockState = world.getBlockState(pos);
      if (blockState.isAir()) {
         return skyColor(world, ray);
      }

      double hitDistance = hit.getLocation().distanceTo(start);
      MapColor mapColor = blockState.getMapColor(world, pos);
      int color = mapColor == MapColor.NONE ? world.getClientLeafTintColor(pos) : mapColor.col;
      double distanceShade = Math.max(0.36, 1.0 - hitDistance / 60.0);
      double sideShade = world.getShade(hit.getDirection(), true);
      double lightShade = 0.52 + world.getRawBrightness(pos, 0) / 15.0 * 0.32;
      double shade = Math.max(0.3, Math.min(1.0, distanceShade * (sideShade * 0.32 + lightShade)));
      return quantizeColor(applyShade(0xFF000000 | color, shade));
   }

   private static PortalEntity findLinkedPortal(PortalEntity portal) {
      if (portal.level() instanceof ClientLevel clientWorld) {
         UUID owner = portal.getOwner();
         PortalType opposite = portal.getPortalType() == PortalType.BLUE ? PortalType.ORANGE : PortalType.BLUE;
         PortalEntity fallback = null;

         for (Entity entity : clientWorld.entitiesForRendering()) {
            if (entity instanceof PortalEntity candidate
               && candidate != portal
               && !candidate.isRemoved()
               && !candidate.isBlackHole()
               && candidate.getPortalType() == opposite) {
               if (owner != null && Objects.equals(owner, candidate.getOwner())) {
                  return candidate;
               }

               if (fallback == null) {
                  fallback = candidate;
               }
            }
         }

         return fallback;
      } else {
         return null;
      }
   }

   private static void renderWallLinkedView(
      PortalRenderer.PortalRenderState state, PoseStack matrices, SubmitNodeCollector renderQueue, RenderType layer, Direction face
   ) {
      if (state.linkedViewPixels.length != 0) {
         renderQueue.submitCustomGeometry(matrices, layer, (entry, vc) -> {
            float planeOffset = wallPlaneOffset(face, 0.006F);

            for (int row = 0; row < 108; row++) {
               float y0 = row / 108.0F * 2.0F;
               float y1 = (row + 1) / 108.0F * 2.0F;
               int runStart = -1;
               int runColor = 0;

               for (int col = 0; col <= 72; col++) {
                  int color = 0;
                  boolean drawable = false;
                  if (col < 72) {
                     float x0 = -0.5F + col / 72.0F;
                     float x1 = -0.5F + (col + 1) / 72.0F;
                     drawable = insideInsetOval(x0, x1, y0 - 1.0F, y1 - 1.0F);
                     if (drawable) {
                        color = state.linkedViewPixels[(108 - row - 1) * 72 + mirroredColumn(col)];
                     }
                  }

                  if (!drawable || runStart < 0 || color != runColor) {
                     if (runStart >= 0) {
                        float x0 = -0.5F + runStart / 72.0F;
                        float x1 = -0.5F + col / 72.0F;
                        coloredWallQuad(vc, entry, face, x0, y0, x1, y1, planeOffset, runColor, state.lightCoords);
                     }

                     runStart = drawable ? col : -1;
                     runColor = color;
                  }
               }
            }
         });
      }
   }

   private static void renderHorizontalLinkedView(
      PortalRenderer.PortalRenderState state, PoseStack matrices, SubmitNodeCollector renderQueue, RenderType layer, float normalY
   ) {
      if (state.linkedViewPixels.length != 0) {
         renderQueue.submitCustomGeometry(matrices, layer, (entry, vc) -> {
            for (int row = 0; row < 108; row++) {
               float z0 = -1.0F + (108 - row - 1) / 108.0F * 2.0F;
               float z1 = -1.0F + (108 - row) / 108.0F * 2.0F;
               int runStart = -1;
               int runColor = 0;

               for (int col = 0; col <= 72; col++) {
                  int color = 0;
                  boolean drawable = false;
                  if (col < 72) {
                     float x0 = -0.5F + col / 72.0F;
                     float x1 = -0.5F + (col + 1) / 72.0F;
                     drawable = insideInsetOval(x0, x1, z0, z1);
                     if (drawable) {
                        color = state.linkedViewPixels[(108 - row - 1) * 72 + mirroredColumn(col)];
                     }
                  }

                  if (!drawable || runStart < 0 || color != runColor) {
                     if (runStart >= 0) {
                        float x0 = -0.5F + runStart / 72.0F;
                        float x1 = -0.5F + col / 72.0F;
                        coloredQuad(vc, entry, x0, 0.006F * normalY, z0, x1, 0.006F * normalY, z1, 0.0F, normalY, 0.0F, runColor, state.lightCoords);
                     }

                     runStart = drawable ? col : -1;
                     runColor = color;
                  }
               }
            }
         });
      }
   }

   private static void renderWallRing(PoseStack matrices, SubmitNodeCollector renderQueue, RenderType layer, int light, Direction face) {
      renderQueue.submitCustomGeometry(matrices, layer, (entry, vc) -> {
         texturedWallQuad(vc, entry, face, -0.5F, 0.0F, 0.0F, 1.0F, light);
         texturedWallQuad(vc, entry, face, 0.5F, 0.0F, 1.0F, 1.0F, light);
         texturedWallQuad(vc, entry, face, 0.5F, 2.0F, 1.0F, 0.0F, light);
         texturedWallQuad(vc, entry, face, -0.5F, 2.0F, 0.0F, 0.0F, light);
         texturedWallQuad(vc, entry, face, -0.5F, 2.0F, 0.0F, 0.0F, light);
         texturedWallQuad(vc, entry, face, 0.5F, 2.0F, 1.0F, 0.0F, light);
         texturedWallQuad(vc, entry, face, 0.5F, 0.0F, 1.0F, 1.0F, light);
         texturedWallQuad(vc, entry, face, -0.5F, 0.0F, 0.0F, 1.0F, light);
      });
   }

   private static void renderHorizontalRing(PoseStack matrices, SubmitNodeCollector renderQueue, RenderType layer, int light, float normalY) {
      renderQueue.submitCustomGeometry(matrices, layer, (entry, vc) -> {
         texturedQuad(vc, entry, -0.5F, 0.026F * normalY, -1.0F, 0.0F, 1.0F, 0.0F, normalY, 0.0F, light);
         texturedQuad(vc, entry, 0.5F, 0.026F * normalY, -1.0F, 1.0F, 1.0F, 0.0F, normalY, 0.0F, light);
         texturedQuad(vc, entry, 0.5F, 0.026F * normalY, 1.0F, 1.0F, 0.0F, 0.0F, normalY, 0.0F, light);
         texturedQuad(vc, entry, -0.5F, 0.026F * normalY, 1.0F, 0.0F, 0.0F, 0.0F, normalY, 0.0F, light);
         texturedQuad(vc, entry, -0.5F, 0.026F * normalY, 1.0F, 0.0F, 0.0F, 0.0F, normalY, 0.0F, light);
         texturedQuad(vc, entry, 0.5F, 0.026F * normalY, 1.0F, 1.0F, 0.0F, 0.0F, normalY, 0.0F, light);
         texturedQuad(vc, entry, 0.5F, 0.026F * normalY, -1.0F, 1.0F, 1.0F, 0.0F, normalY, 0.0F, light);
         texturedQuad(vc, entry, -0.5F, 0.026F * normalY, -1.0F, 0.0F, 1.0F, 0.0F, normalY, 0.0F, light);
      });
   }

   private static boolean insideInsetOval(float x0, float x1, float y0, float y1) {
      float cx = (x0 + x1) * 0.5F;
      float cy = (y0 + y1) * 0.5F;
      return insideInsetOvalCenter(cx, cy);
   }

   private static boolean insideInsetOvalCenter(double x, double y) {
      double nx = x / 0.37F;
      double ny = y / 0.87F;
      return nx * nx + ny * ny <= 1.0;
   }

   private static int mirroredColumn(int col) {
      return 72 - col - 1;
   }

   private static int applyShade(int argb, double shade) {
      int a = argb >>> 24 & 0xFF;
      int r = (int)((argb >>> 16 & 0xFF) * shade);
      int g = (int)((argb >>> 8 & 0xFF) * shade);
      int b = (int)((argb & 0xFF) * shade);
      return a << 24 | clampColor(r) << 16 | clampColor(g) << 8 | clampColor(b);
   }

   private static int skyColor(ClientLevel world, Vec3 ray) {
      int horizon = world.isDarkOutside() ? -15261382 : -3548952;
      int zenith = world.isDarkOutside() ? -16248545 : -9525288;
      double amount = Math.max(0.0, Math.min(1.0, ray.y * 0.5 + 0.5));
      return quantizeColor(blendColor(horizon, zenith, amount));
   }

   private static int quantizeColor(int argb) {
      int a = argb >>> 24 & 0xFF;
      int r = quantizeChannel(argb >>> 16 & 0xFF);
      int g = quantizeChannel(argb >>> 8 & 0xFF);
      int b = quantizeChannel(argb & 0xFF);
      return a << 24 | r << 16 | g << 8 | b;
   }

   private static int quantizeChannel(int value) {
      return clampColor(value / 8 * 8);
   }

   private static int blendColor(int from, int to, double amount) {
      int a = blendChannel(from >>> 24 & 0xFF, to >>> 24 & 0xFF, amount);
      int r = blendChannel(from >>> 16 & 0xFF, to >>> 16 & 0xFF, amount);
      int g = blendChannel(from >>> 8 & 0xFF, to >>> 8 & 0xFF, amount);
      int b = blendChannel(from & 0xFF, to & 0xFF, amount);
      return a << 24 | r << 16 | g << 8 | b;
   }

   private static int blendChannel(int from, int to, double amount) {
      return clampColor((int)Math.round(from + (to - from) * amount));
   }

   private static int clampColor(int value) {
      return Math.max(0, Math.min(255, value));
   }

   private static Vec3 portalCenter(PortalEntity portal) {
      Direction facing = portal.getNearestViewDirection();
      double y = portal.getY();
      if (facing != Direction.UP && facing != Direction.DOWN) {
         y++;
      }

      return new Vec3(portal.getX(), y, portal.getZ());
   }

   private static Vec3 portalNormal(PortalEntity portal) {
      Direction facing = portal.getNearestViewDirection();
      if (facing == null) {
         facing = Direction.SOUTH;
      }

      Vec3 normal = new Vec3(facing.getStepX(), facing.getStepY(), facing.getStepZ());
      return normal.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 0.0, 1.0) : normal.normalize();
   }

   private static PortalRenderer.PortalBasis basisForPortal(PortalEntity portal) {
      Vec3 forward = portalNormal(portal);
      Vec3 up = portalUp(portal, forward);
      Vec3 right = forward.cross(up);
      if (right.lengthSqr() < 1.0E-6) {
         right = new Vec3(1.0, 0.0, 0.0);
      }

      right = right.normalize();
      up = right.cross(forward).normalize();
      return new PortalRenderer.PortalBasis(right, up, forward);
   }

   private static Vec3 portalUp(PortalEntity portal, Vec3 normal) {
      Direction facing = portal.getNearestViewDirection();
      Vec3 up = facing != Direction.UP && facing != Direction.DOWN
         ? new Vec3(0.0, 1.0, 0.0)
         : Vec3.directionFromRotation(0.0F, portal.getYRot());
      if (up.lengthSqr() < 1.0E-6 || Math.abs(up.normalize().dot(normal)) > 0.98) {
         up = new Vec3(0.0, 0.0, 1.0);
      }

      return up.normalize();
   }

   private static Vec3 transformPointThroughPortal(
      Vec3 point, Vec3 sourceCenter, PortalRenderer.PortalBasis sourceBasis, Vec3 targetCenter, PortalRenderer.PortalBasis targetBasis
   ) {
      Vec3 offset = point.subtract(sourceCenter);
      double x = offset.dot(sourceBasis.right);
      double y = offset.dot(sourceBasis.up);
      double z = -offset.dot(sourceBasis.forward);
      return targetCenter.add(targetBasis.right.scale(x))
         .add(targetBasis.up.scale(y))
         .add(targetBasis.forward.scale(z));
   }

   private static Vec3 rotateVectorThroughPortal(Vec3 vec, PortalRenderer.PortalBasis sourceBasis, PortalRenderer.PortalBasis targetBasis) {
      double x = vec.dot(sourceBasis.right);
      double y = vec.dot(sourceBasis.up);
      double z = -vec.dot(sourceBasis.forward);
      return targetBasis.right.scale(x).add(targetBasis.up.scale(y)).add(targetBasis.forward.scale(z));
   }

   private static float wallPlaneOffset(Direction face, float amount) {
      return switch (face) {
         case NORTH, WEST -> -amount;
         default -> amount;
      };
   }

   private static void coloredWallQuad(
      VertexConsumer vc, Pose entry, Direction face, float h0, float y0, float h1, float y1, float planeOffset, int color, int light
   ) {
      float nx = face.getStepX();
      float nz = face.getStepZ();
      if (face != Direction.EAST && face != Direction.WEST) {
         coloredQuad(vc, entry, h0, y0, planeOffset, h1, y0, planeOffset, h1, y1, planeOffset, h0, y1, planeOffset, nx, 0.0F, nz, color, light);
      } else {
         coloredQuad(vc, entry, planeOffset, y0, h0, planeOffset, y0, h1, planeOffset, y1, h1, planeOffset, y1, h0, nx, 0.0F, nz, color, light);
      }
   }

   private static void texturedWallQuad(VertexConsumer vc, Pose entry, Direction face, float h, float y, float u, float v, int light) {
      float nx = face.getStepX();
      float nz = face.getStepZ();
      float planeOffset = wallPlaneOffset(face, 0.026F);
      if (face != Direction.EAST && face != Direction.WEST) {
         texturedQuad(vc, entry, h, y, planeOffset, u, v, nx, 0.0F, nz, light);
      } else {
         texturedQuad(vc, entry, planeOffset, y, h, u, v, nx, 0.0F, nz, light);
      }
   }

   private static float nyForHorizontal(Direction face) {
      return face == Direction.UP ? 1.0F : -1.0F;
   }

   private static Identifier getRingTexture(PortalRenderer.PortalRenderState state) {
      return state.portalType == PortalType.ORANGE ? ORANGE_OVAL : BLUE_OVAL;
   }

   private static void texturedQuad(VertexConsumer vc, Pose entry, float x, float y, float z, float u, float v, float nx, float ny, float nz, int light) {
      vc.addVertex(entry, x, y, z)
         .setColor(255, 255, 255, 255)
         .setUv(u, v)
         .setOverlay(OverlayTexture.NO_OVERLAY)
         .setLight(light)
         .setNormal(entry, nx, ny, nz);
   }

   private static void coloredQuad(
      VertexConsumer vc,
      Pose entry,
      float x0,
      float y0,
      float z0,
      float x1,
      float y1,
      float z1,
      float x2,
      float y2,
      float z2,
      float x3,
      float y3,
      float z3,
      float nx,
      float ny,
      float nz,
      int color,
      int light
   ) {
      int a = color >>> 24 & 0xFF;
      int r = color >>> 16 & 0xFF;
      int g = color >>> 8 & 0xFF;
      int b = color & 0xFF;
      coloredVertex(vc, entry, x0, y0, z0, 0.0F, 1.0F, r, g, b, a, nx, ny, nz, light);
      coloredVertex(vc, entry, x1, y1, z1, 1.0F, 1.0F, r, g, b, a, nx, ny, nz, light);
      coloredVertex(vc, entry, x2, y2, z2, 1.0F, 0.0F, r, g, b, a, nx, ny, nz, light);
      coloredVertex(vc, entry, x3, y3, z3, 0.0F, 0.0F, r, g, b, a, nx, ny, nz, light);
      coloredVertex(vc, entry, x3, y3, z3, 0.0F, 0.0F, r, g, b, a, -nx, -ny, -nz, light);
      coloredVertex(vc, entry, x2, y2, z2, 1.0F, 0.0F, r, g, b, a, -nx, -ny, -nz, light);
      coloredVertex(vc, entry, x1, y1, z1, 1.0F, 1.0F, r, g, b, a, -nx, -ny, -nz, light);
      coloredVertex(vc, entry, x0, y0, z0, 0.0F, 1.0F, r, g, b, a, -nx, -ny, -nz, light);
   }

   private static void coloredQuad(
      VertexConsumer vc, Pose entry, float x0, float y0, float z0, float x1, float y1, float z1, float nx, float ny, float nz, int color, int light
   ) {
      coloredQuad(vc, entry, x0, y0, z0, x1, y0, z0, x1, y1, z1, x0, y1, z1, nx, ny, nz, color, light);
   }

   private static void coloredVertex(
      VertexConsumer vc, Pose entry, float x, float y, float z, float u, float v, int r, int g, int b, int a, float nx, float ny, float nz, int light
   ) {
      vc.addVertex(entry, x, y, z)
         .setColor(r, g, b, a)
         .setUv(u, v)
         .setOverlay(OverlayTexture.NO_OVERLAY)
         .setLight(light)
         .setNormal(entry, nx, ny, nz);
   }

   private record PortalBasis(Vec3 right, Vec3 up, Vec3 forward) {
      private PortalBasis {
      }
   }

   public static final class PortalRenderState extends EntityRenderState {
      private int portalEntityId = -1;
      private PortalType portalType = PortalType.BLUE;
      private Direction facing = Direction.SOUTH;
      private float yaw;
      private boolean hasLinkedPortal;
      private Vec3 sourceCenter;
      private PortalRenderer.PortalBasis sourceBasis;
      private int linkedEntityId = -1;
      private Vec3 linkedCenter;
      private PortalRenderer.PortalBasis linkedBasis;
      private int[] linkedViewPixels = PortalRenderer.EMPTY_VIEW;

      public PortalRenderState() {
      }
   }

   private static final class PortalViewCache {
      private final int linkedEntityId;
      private final Vec3 linkedCenter;
      private final int[] pixels;
      private final long nextRefreshTick;
      private long lastUseTick;

      private PortalViewCache(int linkedEntityId, Vec3 linkedCenter, int[] pixels, long nextRefreshTick, long lastUseTick) {
         this.linkedEntityId = linkedEntityId;
         this.linkedCenter = linkedCenter;
         this.pixels = pixels;
         this.nextRefreshTick = nextRefreshTick;
         this.lastUseTick = lastUseTick;
      }
   }
}
