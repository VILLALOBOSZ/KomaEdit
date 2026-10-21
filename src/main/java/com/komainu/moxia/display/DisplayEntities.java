package com.komainu.moxia.display;

import com.komainu.moxia.shape.Vec3;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.util.math.AffineTransformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Locale;

/**
 * Entre los displays de Minecraft y la lógica pura: leer y poner su {@link DisplayPose}, su tipo y su volumen. Sirve
 * al servidor y al cliente (que además mide los textos con la fuente y los objetos con su modelo).
 */
public final class DisplayEntities {
    private DisplayEntities() {
    }

    public static DisplayKind kind(DisplayEntity entity) {
        return switch (entity) {
            case DisplayEntity.BlockDisplayEntity ignored -> DisplayKind.BLOCK;
            case DisplayEntity.ItemDisplayEntity ignored -> DisplayKind.ITEM;
            default -> DisplayKind.TEXT;
        };
    }

    public static DisplayPose pose(DisplayEntity entity) {
        AffineTransformation transformation = DisplayEntity.getTransformation(entity.getDataTracker());
        return new DisplayPose(new Vec3(entity.getX(), entity.getY(), entity.getZ()), entity.getYaw(),
                entity.getPitch(), vector(transformation.getTranslation()), quat(transformation.getLeftRotation()),
                vector(transformation.getScale()), quat(transformation.getRightRotation()),
                DisplayPose.Billboard.of(entity.getBillboardMode().ordinal()));
    }

    /** Pone la pose (posición, giro de la entidad, transformación y billboard). */
    public static void apply(DisplayEntity entity, DisplayPose pose) {
        entity.refreshPositionAndAngles(pose.position().x(), pose.position().y(), pose.position().z(),
                (float) pose.yaw(), (float) pose.pitch());
        entity.setTransformation(transformation(pose));
        entity.setBillboardMode(DisplayEntity.BillboardMode.values()[pose.billboard().ordinal()]);
    }

    public static AffineTransformation transformation(DisplayPose pose) {
        return new AffineTransformation(vector(pose.translation()), quat(pose.left().normalized()),
                vector(pose.scale()), quat(pose.right().normalized()));
    }

    /**
     * El volumen como lo sabe el servidor: el bloque exacto; el objeto como el cubo de su modo de vista (sin el modelo,
     * que solo tiene el cliente); el texto estimado por su largo.
     */
    public static DisplayVolume serverVolume(DisplayEntity entity) {
        return switch (entity) {
            case DisplayEntity.BlockDisplayEntity ignored -> DisplayVolume.block();
            case DisplayEntity.ItemDisplayEntity item -> itemVolume(item.getTransformationMode());
            case DisplayEntity.TextDisplayEntity text -> DisplayVolume.estimatedText(text.getText().getString(),
                    text.getLineWidth());
            default -> DisplayVolume.block();
        };
    }

    /** Un objeto sin su modelo: el tamaño habitual de un bloque en cada modo de vista. */
    public static DisplayVolume itemVolume(ModelTransformationMode mode) {
        double half = switch (mode) {
            case GROUND -> 0.125;
            case FIXED -> 0.25;
            case GUI -> 0.3125;
            case THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND -> 0.1875;
            case FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND -> 0.2;
            default -> 0.5;
        };
        return DisplayVolume.box(new Vec3(-half, -half, -half), new Vec3(half, half, half));
    }

    /** Un modo de vista por su nombre de Minecraft o en español. */
    public static ModelTransformationMode itemMode(String text) {
        String value = text == null ? "" : text.strip().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "fixed", "fijo", "marco" -> ModelTransformationMode.FIXED;
            case "none", "ninguno", "modelo" -> ModelTransformationMode.NONE;
            case "gui", "inventario", "icono" -> ModelTransformationMode.GUI;
            case "ground", "suelo" -> ModelTransformationMode.GROUND;
            case "head", "cabeza", "sombrero" -> ModelTransformationMode.HEAD;
            case "thirdperson_righthand", "mano", "mano_derecha" -> ModelTransformationMode.THIRD_PERSON_RIGHT_HAND;
            case "thirdperson_lefthand", "mano_izquierda" -> ModelTransformationMode.THIRD_PERSON_LEFT_HAND;
            case "firstperson_righthand", "primera_persona" -> ModelTransformationMode.FIRST_PERSON_RIGHT_HAND;
            case "firstperson_lefthand" -> ModelTransformationMode.FIRST_PERSON_LEFT_HAND;
            default -> throw new IllegalArgumentException("Modo de vista desconocido: " + text
                    + " (fixed, none, gui, ground, head o mano)");
        };
    }

    public static Vec3 vector(Vector3f vector) {
        return new Vec3(vector.x(), vector.y(), vector.z());
    }

    public static Vector3f vector(Vec3 vector) {
        return new Vector3f((float) vector.x(), (float) vector.y(), (float) vector.z());
    }

    public static Quat quat(Quaternionf quaternion) {
        return new Quat(quaternion.x(), quaternion.y(), quaternion.z(), quaternion.w());
    }

    public static Quaternionf quat(Quat quaternion) {
        return new Quaternionf((float) quaternion.x(), (float) quaternion.y(), (float) quaternion.z(),
                (float) quaternion.w());
    }
}
