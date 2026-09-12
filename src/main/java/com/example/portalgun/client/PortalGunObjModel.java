package com.example.portalgun.client;

import com.example.portalgun.PortalGunMod;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import org.joml.Vector3f;
import org.joml.Vector3fc;

final class PortalGunObjModel {
   private static final Identifier MODEL_ID = Identifier.fromNamespaceAndPath("portalgun", "models/item/portal_gun_blue.obj");
   private static final Identifier MATERIAL_TEXTURE = Identifier.fromNamespaceAndPath("portalgun", "textures/item/portalgun_white.png");
   private static final RenderType OPAQUE_LAYER = RenderTypes.entityCutout(MATERIAL_TEXTURE);
   private static final RenderType LIT_LAYER = RenderTypes.entityCutout(MATERIAL_TEXTURE);
   private static final float TARGET_LENGTH = 1.25F;
   private static final float REAR_X = 0.125F;
   private static final float ITEM_CENTER = 0.5F;
   private static final float CLAW_FORWARD_FLARE = 0.04F;
   private static final float CLAW_OUTWARD_FLARE = 0.12F;
   private static final float MUZZLE_FLASH_CENTER_X = 1.186F;
   private static final float MUZZLE_FLASH_CENTER_Y = 0.5F;
   private static final float MUZZLE_FLASH_CENTER_Z = 0.556F;
   private static final PortalGunObjModel EMPTY = new PortalGunObjModel(new PortalGunObjModel.Triangle[0], new PortalGunObjModel.Triangle[0], defaultVertices());
   private static volatile PortalGunObjModel current = EMPTY;
   private final PortalGunObjModel.Triangle[] opaqueTriangles;
   private final PortalGunObjModel.Triangle[] emissiveTriangles;
   private final Vector3fc[] boundsVertices;

   private PortalGunObjModel(PortalGunObjModel.Triangle[] opaqueTriangles, PortalGunObjModel.Triangle[] emissiveTriangles, Vector3fc[] boundsVertices) {
      this.opaqueTriangles = opaqueTriangles;
      this.emissiveTriangles = emissiveTriangles;
      this.boundsVertices = boundsVertices;
   }

   static PortalGunObjModel current() {
      return current;
   }

   static void reload(ResourceManager resourceManager) {
      try (BufferedReader reader = resourceManager.openAsReader(MODEL_ID)) {
         current = parse(reader);
         PortalGunMod.LOGGER
            .info(
               "Loaded smooth Portal Gun model: {} opaque triangles, {} emissive triangles", current.opaqueTriangles.length, current.emissiveTriangles.length
            );
      } catch (IOException | RuntimeException e) {
         current = EMPTY;
         PortalGunMod.LOGGER.error("Failed to load smooth Portal Gun model {}", MODEL_ID, e);
      }
   }

   void render(PoseStack matrices, SubmitNodeCollector renderQueue, int light, int overlay, int firingTicks, boolean grabActive) {
      if (this.opaqueTriangles.length > 0) {
         renderQueue.submitCustomGeometry(
            matrices,
            OPAQUE_LAYER,
            (entry, vertexConsumer) -> renderTriangles(this.opaqueTriangles, entry, vertexConsumer, light, overlay, firingTicks, grabActive)
         );
      }

      if (this.emissiveTriangles.length > 0) {
         renderQueue.submitCustomGeometry(
            matrices,
            LIT_LAYER,
            (entry, vertexConsumer) -> renderTriangles(this.emissiveTriangles, entry, vertexConsumer, 15728880, overlay, firingTicks, grabActive)
         );
      }
   }

   void collectVertices(Consumer<Vector3fc> consumer) {
      for (Vector3fc vertex : this.boundsVertices) {
         consumer.accept(new Vector3f(vertex));
      }
   }

   private static void renderTriangles(
      PortalGunObjModel.Triangle[] triangles, Pose entry, VertexConsumer vertexConsumer, int light, int overlay, int firingTicks, boolean grabActive
   ) {
      for (PortalGunObjModel.Triangle triangle : triangles) {
         triangle.render(entry, vertexConsumer, light, overlay, firingTicks, grabActive);
      }
   }

   private static PortalGunObjModel parse(BufferedReader reader) throws IOException {
      List<Vector3f> positions = new ArrayList<>();
      List<Vector3f> normals = new ArrayList<>();
      List<PortalGunObjModel.RawFace> faces = new ArrayList<>();
      String objectName = "default";
      int lineNumber = 0;

      String line;
      while ((line = reader.readLine()) != null) {
         lineNumber++;
         line = line.trim();
         if (!line.isEmpty() && !line.startsWith("#")) {
            String[] parts = line.split("\\s+");
            switch (parts[0]) {
               case "o":
                  objectName = line.length() > 2 ? line.substring(2).trim() : "unnamed";
                  break;
               case "v":
                  positions.add(parseVector(parts, lineNumber));
                  break;
               case "vn":
                  normals.add(parseVector(parts, lineNumber));
                  break;
               case "f":
                  addFaces(parts, objectName, positions.size(), normals.size(), faces, lineNumber);
            }
         }
      }

      if (positions.isEmpty()) {
         throw new IOException("OBJ has no vertices");
      }

      PortalGunObjModel.Bounds rawBounds = PortalGunObjModel.Bounds.from(positions);
      float scale = 1.25F / Math.max(rawBounds.maxX - rawBounds.minX, 1.0E-4F);
      List<PortalGunObjModel.Triangle> opaque = new ArrayList<>();
      List<PortalGunObjModel.Triangle> emissive = new ArrayList<>();
      PortalGunObjModel.Bounds transformedBounds = new PortalGunObjModel.Bounds(
         Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY
      );

      for (PortalGunObjModel.RawFace face : faces) {
         PortalGunObjModel.MeshMaterial material = materialFor(face.objectName());
         PortalGunObjModel.Vertex a = transformVertex(face.vertices()[0], positions, normals, rawBounds, scale);
         PortalGunObjModel.Vertex b = transformVertex(face.vertices()[1], positions, normals, rawBounds, scale);
         PortalGunObjModel.Vertex c = transformVertex(face.vertices()[2], positions, normals, rawBounds, scale);
         if (!a.hasNormal() || !b.hasNormal() || !c.hasNormal()) {
            Vector3f faceNormal = computeFaceNormal(a, b, c);
            a = a.withNormal(faceNormal);
            b = b.withNormal(faceNormal);
            c = c.withNormal(faceNormal);
         }

         transformedBounds.include(a.position());
         transformedBounds.include(b.position());
         transformedBounds.include(c.position());
         PortalGunObjModel.Triangle triangle = new PortalGunObjModel.Triangle(a, b, c, material, PortalGunObjModel.AnimatedPart.forObject(face.objectName()));
         if (material.emissive()) {
            emissive.add(triangle);
         } else {
            opaque.add(triangle);
         }
      }

      return new PortalGunObjModel(
         opaque.toArray(PortalGunObjModel.Triangle[]::new), emissive.toArray(PortalGunObjModel.Triangle[]::new), transformedBounds.toVertices()
      );
   }

   private static Vector3f parseVector(String[] parts, int lineNumber) throws IOException {
      if (parts.length < 4) {
         throw new IOException("Malformed vector at OBJ line " + lineNumber);
      }

      try {
         return new Vector3f(Float.parseFloat(parts[1]), Float.parseFloat(parts[2]), Float.parseFloat(parts[3]));
      } catch (NumberFormatException e) {
         throw new IOException("Malformed number at OBJ line " + lineNumber, e);
      }
   }

   private static void addFaces(String[] parts, String objectName, int positionCount, int normalCount, List<PortalGunObjModel.RawFace> faces, int lineNumber) throws IOException {
      if (parts.length < 4) {
         throw new IOException("Malformed face at OBJ line " + lineNumber);
      }

      PortalGunObjModel.FaceRef[] refs = new PortalGunObjModel.FaceRef[parts.length - 1];

      for (int i = 1; i < parts.length; i++) {
         refs[i - 1] = parseFaceRef(parts[i], positionCount, normalCount, lineNumber);
      }

      for (int i = 1; i < refs.length - 1; i++) {
         faces.add(new PortalGunObjModel.RawFace(objectName, new PortalGunObjModel.FaceRef[]{refs[0], refs[i], refs[i + 1]}));
      }
   }

   private static PortalGunObjModel.FaceRef parseFaceRef(String token, int positionCount, int normalCount, int lineNumber) throws IOException {
      String[] parts = token.split("/", -1);
      if (parts.length != 0 && !parts[0].isEmpty()) {
         int positionIndex = parseObjIndex(parts[0], positionCount, lineNumber);
         int normalIndex = -1;
         if (parts.length >= 3 && !parts[2].isEmpty()) {
            normalIndex = parseObjIndex(parts[2], normalCount, lineNumber);
         }

         return new PortalGunObjModel.FaceRef(positionIndex, normalIndex);
      } else {
         throw new IOException("Face without vertex index at OBJ line " + lineNumber);
      }
   }

   private static int parseObjIndex(String value, int size, int lineNumber) throws IOException {
      try {
         int raw = Integer.parseInt(value);
         int index = raw < 0 ? size + raw : raw - 1;
         if (index >= 0 && index < size) {
            return index;
         } else {
            throw new IOException("OBJ index out of range at line " + lineNumber + ": " + value);
         }
      } catch (NumberFormatException e) {
         throw new IOException("Malformed OBJ index at line " + lineNumber + ": " + value, e);
      }
   }

   private static PortalGunObjModel.Vertex transformVertex(
      PortalGunObjModel.FaceRef ref, List<Vector3f> positions, List<Vector3f> normals, PortalGunObjModel.Bounds rawBounds, float scale
   ) {
      Vector3f rawPosition = positions.get(ref.positionIndex());
      float centerY = (rawBounds.minY + rawBounds.maxY) * 0.5F;
      float centerZ = (rawBounds.minZ + rawBounds.maxZ) * 0.5F;
      Vector3f position = new Vector3f(
         (rawBounds.maxX - rawPosition.x) * scale + 0.125F, (rawPosition.y - centerY) * scale + 0.5F, (centerZ - rawPosition.z) * scale + 0.5F
      );
      Vector3f normal = null;
      if (ref.normalIndex() >= 0) {
         Vector3f rawNormal = normals.get(ref.normalIndex());
         normal = new Vector3f(-rawNormal.x, rawNormal.y, -rawNormal.z);
         if (normal.lengthSquared() > 1.0E-6F) {
            normal.normalize();
         }
      }

      return new PortalGunObjModel.Vertex(position, normal);
   }

   private static Vector3f computeFaceNormal(PortalGunObjModel.Vertex a, PortalGunObjModel.Vertex b, PortalGunObjModel.Vertex c) {
      Vector3f edge1 = new Vector3f(b.position()).sub(a.position());
      Vector3f edge2 = new Vector3f(c.position()).sub(a.position());
      Vector3f normal = edge1.cross(edge2);
      return normal.lengthSquared() <= 1.0E-6F ? new Vector3f(0.0F, 1.0F, 0.0F) : normal.normalize();
   }

   private static PortalGunObjModel.MeshMaterial materialFor(String objectName) {
      String name = objectName.toLowerCase(Locale.ROOT);
      if (!name.contains("blue") && !name.equals("energy_glass") && !name.equals("energy_core") && !name.equals("energy_inner_glow")) {
         if (name.contains("white")) {
            return name.contains("shroud") ? PortalGunObjModel.MeshMaterial.WHITE_SHROUD : PortalGunObjModel.MeshMaterial.WHITE_SHELL;
         } else {
            return !name.contains("ring") && !name.contains("collar") && !name.contains("barrel")
               ? PortalGunObjModel.MeshMaterial.BLACK
               : PortalGunObjModel.MeshMaterial.DARK_METAL;
         }
      } else {
         return !name.contains("glass") && !name.contains("glow") ? PortalGunObjModel.MeshMaterial.BLUE : PortalGunObjModel.MeshMaterial.BLUE_GLOW;
      }
   }

   private static Vector3fc[] defaultVertices() {
      return new Vector3fc[]{
         new Vector3f(0.125F, 0.25F, 0.25F),
         new Vector3f(1.375F, 0.25F, 0.25F),
         new Vector3f(1.375F, 0.75F, 0.25F),
         new Vector3f(0.125F, 0.75F, 0.25F),
         new Vector3f(0.125F, 0.25F, 0.75F),
         new Vector3f(1.375F, 0.25F, 0.75F),
         new Vector3f(1.375F, 0.75F, 0.75F),
         new Vector3f(0.125F, 0.75F, 0.75F)
      };
   }

   private static float shotStrength(int firingTicks) {
      float time = Math.max(0.0F, Math.min(1.0F, firingTicks / 12.0F));
      return time * time * (3.0F - 2.0F * time);
   }

   private enum AnimatedPart {
      STATIC(0.0F, 0.0F),
      CLAW_SIDE(0.0F, -1.0F),
      CLAW_LOWER(-0.86F, 0.5F),
      CLAW_UPPER(0.86F, 0.5F),
      MUZZLE_GLOW(0.0F, 0.0F),
      ENERGY_CORE(0.0F, 0.0F);

      private final float openY;
      private final float openZ;

      AnimatedPart(float openY, float openZ) {
         this.openY = openY;
         this.openZ = openZ;
      }

      static PortalGunObjModel.AnimatedPart forObject(String objectName) {
         String name = objectName.toLowerCase(Locale.ROOT);
         if (name.startsWith("claw_0_")) {
            return CLAW_SIDE;
         } else if (name.startsWith("claw_1_")) {
            return CLAW_LOWER;
         } else if (name.startsWith("claw_2_")) {
            return CLAW_UPPER;
         } else if (name.startsWith("muzzle_blue_")) {
            return MUZZLE_GLOW;
         } else {
            return !name.equals("energy_core") && !name.equals("energy_inner_glow") ? STATIC : ENERGY_CORE;
         }
      }

      boolean isClaw() {
         return this == CLAW_SIDE || this == CLAW_LOWER || this == CLAW_UPPER;
      }

      float openY() {
         return this.openY;
      }

      float openZ() {
         return this.openZ;
      }
   }

   private static final class Bounds {
      private float minX;
      private float maxX;
      private float minY;
      private float maxY;
      private float minZ;
      private float maxZ;

      private Bounds(float minX, float maxX, float minY, float maxY, float minZ, float maxZ) {
         this.minX = minX;
         this.maxX = maxX;
         this.minY = minY;
         this.maxY = maxY;
         this.minZ = minZ;
         this.maxZ = maxZ;
      }

      private static PortalGunObjModel.Bounds from(List<Vector3f> vertices) {
         PortalGunObjModel.Bounds bounds = new PortalGunObjModel.Bounds(
            Float.POSITIVE_INFINITY,
            Float.NEGATIVE_INFINITY,
            Float.POSITIVE_INFINITY,
            Float.NEGATIVE_INFINITY,
            Float.POSITIVE_INFINITY,
            Float.NEGATIVE_INFINITY
         );

         for (Vector3f vertex : vertices) {
            bounds.include(vertex);
         }

         return bounds;
      }

      private void include(Vector3f vertex) {
         this.minX = Math.min(this.minX, vertex.x);
         this.maxX = Math.max(this.maxX, vertex.x);
         this.minY = Math.min(this.minY, vertex.y);
         this.maxY = Math.max(this.maxY, vertex.y);
         this.minZ = Math.min(this.minZ, vertex.z);
         this.maxZ = Math.max(this.maxZ, vertex.z);
      }

      private Vector3fc[] toVertices() {
         return new Vector3fc[]{
            new Vector3f(this.minX, this.minY, this.minZ),
            new Vector3f(this.maxX, this.minY, this.minZ),
            new Vector3f(this.maxX, this.maxY, this.minZ),
            new Vector3f(this.minX, this.maxY, this.minZ),
            new Vector3f(this.minX, this.minY, this.maxZ),
            new Vector3f(this.maxX, this.minY, this.maxZ),
            new Vector3f(this.maxX, this.maxY, this.maxZ),
            new Vector3f(this.minX, this.maxY, this.maxZ)
         };
      }
   }

   private record FaceRef(int positionIndex, int normalIndex) {
      private FaceRef {
      }
   }

   private enum MeshMaterial {
      WHITE_SHELL(238, 242, 246, 255, false, false),
      WHITE_SHROUD(248, 248, 244, 255, false, false),
      DARK_METAL(38, 40, 46, 255, false, false),
      BLACK(15, 17, 21, 255, false, false),
      BLUE(55, 168, 255, 255, true, true),
      BLUE_GLOW(50, 190, 255, 210, true, true);

      private final int red;
      private final int green;
      private final int blue;
      private final int alpha;
      private final boolean emissive;
      private final boolean pulses;

      MeshMaterial(int red, int green, int blue, int alpha, boolean emissive, boolean pulses) {
         this.red = red;
         this.green = green;
         this.blue = blue;
         this.alpha = alpha;
         this.emissive = emissive;
         this.pulses = pulses;
      }

      int red() {
         return this.red;
      }

      int green() {
         return this.green;
      }

      int blue() {
         return this.blue;
      }

      int alpha() {
         return this.alpha;
      }

      boolean emissive() {
         return this.emissive;
      }

      boolean pulses() {
         return this.pulses;
      }
   }

   private record RawFace(String objectName, PortalGunObjModel.FaceRef[] vertices) {
      private RawFace {
      }
   }

   private record Triangle(
      PortalGunObjModel.Vertex a,
      PortalGunObjModel.Vertex b,
      PortalGunObjModel.Vertex c,
      PortalGunObjModel.MeshMaterial material,
      PortalGunObjModel.AnimatedPart part
   ) {
      private Triangle {
      }

      void render(Pose entry, VertexConsumer vertexConsumer, int light, int overlay, int firingTicks, boolean grabActive) {
         this.emit(entry, vertexConsumer, this.a, light, overlay, firingTicks, grabActive);
         this.emit(entry, vertexConsumer, this.b, light, overlay, firingTicks, grabActive);
         this.emit(entry, vertexConsumer, this.c, light, overlay, firingTicks, grabActive);
         this.emit(entry, vertexConsumer, this.c, light, overlay, firingTicks, grabActive);
      }

      private void emit(
         Pose entry, VertexConsumer vertexConsumer, PortalGunObjModel.Vertex vertex, int light, int overlay, int firingTicks, boolean grabActive
      ) {
         int pulse = this.material.pulses() ? Math.min(90, firingTicks * 8) : 0;
         if (grabActive && this.material.pulses()) {
            pulse = Math.max(pulse, 35);
         }

         int red = Math.min(255, this.material.red() + pulse);
         int green = Math.min(255, this.material.green() + pulse);
         int blue = Math.min(255, this.material.blue() + pulse);
         Vector3fc position = vertex.position();
         float x = position.x();
         float y = position.y();
         float z = position.z();
         float shot = firingTicks > 0 ? PortalGunObjModel.shotStrength(firingTicks) : 0.0F;
         if (this.part.isClaw()) {
            float tip = Math.max(0.0F, Math.min(1.0F, (x - 0.92F) / 0.46F));
            float open = Math.max(shot, grabActive ? 0.85F : 0.0F) * tip;
            x += 0.04F * open;
            y += this.part.openY() * 0.12F * open;
            z += this.part.openZ() * 0.12F * open;
         } else if (firingTicks > 0) {
            if (this.part == PortalGunObjModel.AnimatedPart.MUZZLE_GLOW) {
               float scale = 1.0F + 0.75F * shot;
               x = 1.186F + (x - 1.186F) * (1.0F + 0.35F * shot) + 0.04F * shot;
               y = 0.5F + (y - 0.5F) * scale;
               z = 0.556F + (z - 0.556F) * scale;
            } else if (this.part == PortalGunObjModel.AnimatedPart.ENERGY_CORE) {
               float scale = 1.0F + 0.22F * shot;
               y = 0.5F + (y - 0.5F) * scale;
               z = 0.556F + (z - 0.556F) * scale;
            }
         }

         vertexConsumer.addVertex(entry, x, y, z)
            .setColor(red, green, blue, this.material.alpha())
            .setUv(0.0F, 0.0F)
            .setOverlay(overlay)
            .setLight(light)
            .setNormal(entry, vertex.normal().x, vertex.normal().y, vertex.normal().z);
      }
   }

   private record Vertex(Vector3f position, Vector3f normal) {
      private Vertex {
      }

      boolean hasNormal() {
         return this.normal != null;
      }

      PortalGunObjModel.Vertex withNormal(Vector3f newNormal) {
         return this.hasNormal() ? this : new PortalGunObjModel.Vertex(this.position, new Vector3f(newNormal));
      }
   }
}
