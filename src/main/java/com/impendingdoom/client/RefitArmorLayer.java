package com.impendingdoom.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.EquipmentAssetManager;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.equipment.Equippable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/**
 * Renders player armor on mobs whose models do not normally render armor (spiders, creepers, illagers, endermen,
 * ghasts, modded mobs, ...), fitted to the mob's own model.
 * <p>
 * Every cube of the mob's model is assigned an armor piece (head parts get the helmet, body and arm parts the
 * chestplate, leg parts the leggings and boots). The armor shell for that cube is the cube itself, posed exactly like
 * the mob's part, grown outward on each axis by the same proportion the armor stands off the player model:
 * <ul>
 *   <li>helmet: the player's 8x8x8 head is grown by 1px per side, so a mob head grows by 2/8 of its size per axis;</li>
 *   <li>chestplate: the 8x12x4 body and 4x12x4 arms grow 1px per side (x+2/8, y+2/12, z+2/4 for the body);</li>
 *   <li>leggings: the 4x12x4 legs grow 0.5px per side; boots grow 1px per side.</li>
 * </ul>
 * The armor texture is mapped onto each shell using the matching part of the player armor texture (head, body, arm
 * or leg), stretched to the shell's size.
 */
public class RefitArmorLayer<S extends LivingEntityRenderState, M extends EntityModel<? super S>> extends RenderLayer<S, M> {
    /** Which part of the humanoid armor texture, and how far it stands off, for one kind of mob part. */
    private enum Fit {
        HEAD(EquipmentSlot.HEAD, 0, 0, 8, 8, 8, 1.0F),
        BODY(EquipmentSlot.CHEST, 16, 16, 8, 12, 4, 1.0F),
        ARM(EquipmentSlot.CHEST, 40, 16, 4, 12, 4, 1.0F),
        LEGGINGS(EquipmentSlot.LEGS, 0, 16, 4, 12, 4, 0.5F),
        BOOTS(EquipmentSlot.FEET, 0, 16, 4, 12, 4, 1.0F);

        final EquipmentSlot slot;
        final ModelPart.Cube reference;
        final float w, h, d, grow;

        Fit(EquipmentSlot slot, int u, int v, float w, float h, float d, float grow) {
            this.slot = slot;
            this.w = w;
            this.h = h;
            this.d = d;
            this.grow = grow;
            // A cube of the player part's size, centered on the origin, with the vanilla box UV layout (64x32 armor texture).
            this.reference = new ModelPart.Cube(u, v, -w / 2, -h / 2, -d / 2, w, h, d, 0, 0, 0, false, 64, 32, EnumSet.allOf(Direction.class));
        }

        /** Growth factor per axis: (player part size + 2 * stand-off) / player part size. */
        float fx() { return (w + 2 * grow) / w; }
        float fy() { return (h + 2 * grow) / h; }
        float fz() { return (d + 2 * grow) / d; }
    }

    private enum Kind { HEAD, BODY, ARM, LEG }

    private record Piece(Kind kind, PoseStack.Pose pose, ModelPart.Cube cube) {}

    private static Field childrenField;
    private static Field cubesField;

    private final EquipmentAssetManager equipmentAssets;

    public RefitArmorLayer(RenderLayerParent<S, M> parent, EntityRendererProvider.Context context) {
        super(parent);
        this.equipmentAssets = context.getEquipmentAssets();
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, S state, float yRot, float xRot) {
        ItemStack[] armor = state.getRenderData(ArmorRenderData.ARMOR);
        if (armor == null) return;

        // Pose the mob's model for this frame and collect its cubes with their final transforms.
        M model = getParentModel();
        model.setupAnim(state);
        float scale = state.scale <= 0 ? 1.0F : state.scale;
        float modelHeight = Math.max(0.1F, state.boundingBoxHeight / scale);
        List<Piece> pieces = new ArrayList<>();
        PoseStack modelSpace = new PoseStack();
        collect(model.root(), "root", null, poseStack, modelSpace, modelHeight, pieces);
        if (pieces.isEmpty()) return;

        submitSlot(collector, poseStack, light, state, armor[0], EquipmentSlot.HEAD, pieces);
        submitSlot(collector, poseStack, light, state, armor[1], EquipmentSlot.CHEST, pieces);
        submitSlot(collector, poseStack, light, state, armor[2], EquipmentSlot.LEGS, pieces);
        submitSlot(collector, poseStack, light, state, armor[3], EquipmentSlot.FEET, pieces);
    }

    private void submitSlot(SubmitNodeCollector collector, PoseStack poseStack, int light, S state, ItemStack stack,
                            EquipmentSlot slot, List<Piece> pieces) {
        if (stack == null || stack.isEmpty()) return;
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        if (equippable == null || equippable.slot() != slot || equippable.assetId().isEmpty()) return;
        EquipmentClientInfo.LayerType layerType = slot == EquipmentSlot.LEGS
                ? EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS : EquipmentClientInfo.LayerType.HUMANOID;
        List<EquipmentClientInfo.Layer> layers = equipmentAssets.get(equippable.assetId().get()).getLayers(layerType);

        List<Piece> fitted = new ArrayList<>();
        List<Fit> fits = new ArrayList<>();
        for (Piece piece : pieces) {
            Fit fit = fitFor(slot, piece.kind());
            if (fit != null) {
                fitted.add(piece);
                fits.add(fit);
            }
        }
        if (fitted.isEmpty()) return;

        for (EquipmentClientInfo.Layer layer : layers) {
            int color = 0xFFFFFFFF;
            if (layer.dyeable().isPresent()) {
                int undyed = layer.dyeable().get().colorWhenUndyed().orElse(0);
                color = ARGB.opaque(DyedItemColor.getOrDefault(stack, undyed));
            }
            Identifier texture = layer.textureId().withPath(p -> "textures/entity/equipment/" + layerType.getSerializedName() + "/" + p + ".png");
            int finalColor = color;
            collector.submitCustomGeometry(poseStack, RenderTypes.armorCutoutNoCull(texture), (pose, consumer) -> {
                for (int i = 0; i < fitted.size(); i++) emit(fitted.get(i), fits.get(i), consumer, light, finalColor);
            });
        }
        if (stack.hasFoil() && !layers.isEmpty()) {
            Identifier glintTexture = layers.get(0).textureId().withPath(p -> "textures/entity/equipment/" + layerType.getSerializedName() + "/" + p + ".png");
            collector.submitCustomGeometry(poseStack, RenderTypes.armorCutoutNoCullGlint(glintTexture), (pose, consumer) -> {
                for (int i = 0; i < fitted.size(); i++) emit(fitted.get(i), fits.get(i), consumer, light, 0xFFFFFFFF);
            });
        }
    }

    /** Draws the armor shell of one mob cube: the reference player part stretched over the grown cube. */
    private static void emit(Piece piece, Fit fit, com.mojang.blaze3d.vertex.VertexConsumer consumer, int light, int color) {
        ModelPart.Cube c = piece.cube();
        float sx = c.maxX - c.minX, sy = c.maxY - c.minY, sz = c.maxZ - c.minZ;
        if (sx <= 0.01F && sy <= 0.01F && sz <= 0.01F) return;
        // Flat cubes (wings, fins) still get a sliver of thickness so the shell is visible.
        sx = Math.max(sx, 0.5F);
        sy = Math.max(sy, 0.5F);
        sz = Math.max(sz, 0.5F);
        PoseStack ps = new PoseStack();
        ps.last().pose().set(piece.pose().pose());
        ps.last().normal().set(piece.pose().normal());
        ps.translate((c.minX + c.maxX) / 32.0F, (c.minY + c.maxY) / 32.0F, (c.minZ + c.maxZ) / 32.0F);
        ps.scale(sx * fit.fx() / fit.w, sy * fit.fy() / fit.h, sz * fit.fz() / fit.d);
        fit.reference.compile(ps.last(), consumer, light, OverlayTexture.NO_OVERLAY, color);
    }

    private static Fit fitFor(EquipmentSlot slot, Kind kind) {
        return switch (slot) {
            case HEAD -> kind == Kind.HEAD ? Fit.HEAD : null;
            case CHEST -> kind == Kind.BODY ? Fit.BODY : kind == Kind.ARM ? Fit.ARM : null;
            case LEGS -> kind == Kind.LEG ? Fit.LEGGINGS : null;
            case FEET -> kind == Kind.LEG ? Fit.BOOTS : null;
            default -> null;
        };
    }

    /** Walks the posed model tree, recording every visible cube and what kind of body part it belongs to. */
    private static void collect(ModelPart part, String name, Kind inherited, PoseStack render, PoseStack model, float modelHeight, List<Piece> out) {
        if (!part.visible) return;
        render.pushPose();
        model.pushPose();
        part.translateAndRotate(render);
        part.translateAndRotate(model);
        Kind named = classifyName(name);
        Kind kind = named != null ? named : inherited;
        if (!part.skipDraw) {
            for (ModelPart.Cube cube : cubes(part)) {
                Kind cubeKind = kind;
                if (cubeKind == null) {
                    org.joml.Vector3f center = model.last().pose().transformPosition(
                            (cube.minX + cube.maxX) / 32.0F, (cube.minY + cube.maxY) / 32.0F, (cube.minZ + cube.maxZ) / 32.0F, new org.joml.Vector3f());
                    cubeKind = classifyHeight((1.501F - center.y()) / modelHeight);
                }
                out.add(new Piece(cubeKind, render.last().copy(), cube));
            }
        }
        for (Map.Entry<String, ModelPart> child : children(part).entrySet()) {
            collect(child.getValue(), child.getKey(), kind, render, model, modelHeight, out);
        }
        model.popPose();
        render.popPose();
    }

    private static Kind classifyName(String raw) {
        String name = raw.toLowerCase(java.util.Locale.ROOT);
        // Legs first: names like "rear_left_leg" would otherwise match "ear".
        if (name.contains("leg") || name.contains("foot") || name.contains("feet") || name.contains("thigh") || name.contains("shin")) return Kind.LEG;
        if (name.contains("arm") || name.contains("hand")) return Kind.ARM;
        if (name.contains("body") || name.contains("torso") || name.contains("chest") || name.contains("neck") || name.contains("waist")
                || name.contains("jacket") || name.contains("belly")) return Kind.BODY;
        if (name.contains("head") || name.contains("hat") || name.contains("skull") || name.contains("jaw") || name.contains("nose")
                || name.contains("ear") || name.contains("horn") || name.contains("beak") || name.contains("mouth") || name.contains("snout")
                || name.contains("eye")) return Kind.HEAD;
        return null;
    }

    /** Unnamed parts (tentacles, tails, ...) are classified by how high on the mob they sit (0 = feet, 1 = top). */
    private static Kind classifyHeight(float t) {
        if (t > 0.75F) return Kind.HEAD;
        if (t < 0.3F) return Kind.LEG;
        return Kind.BODY;
    }

    @SuppressWarnings("unchecked")
    private static List<ModelPart.Cube> cubes(ModelPart part) {
        try {
            if (cubesField == null) {
                cubesField = ModelPart.class.getDeclaredField("cubes");
                cubesField.setAccessible(true);
            }
            return (List<ModelPart.Cube>) cubesField.get(part);
        } catch (ReflectiveOperationException e) {
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, ModelPart> children(ModelPart part) {
        try {
            if (childrenField == null) {
                childrenField = ModelPart.class.getDeclaredField("children");
                childrenField.setAccessible(true);
            }
            return (Map<String, ModelPart>) childrenField.get(part);
        } catch (ReflectiveOperationException e) {
            return Map.of();
        }
    }
}
