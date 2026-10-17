package com.komainu.moxia.placement;

import com.komainu.moxia.shape.Vec3;

/**
 * La estructura escrita en el comando, antes de leer el portapapeles, los schematics o los patrones (eso pasa en el
 * hilo del servidor). Notación de ezEdits:
 *
 * <ul>
 *     <li>{@code Clipboard(Origin:INHERENT|CENTER,PasteMethod:FAST|SMOOTHED,FillBias:1,Rotation:0)} ({@code Cl})</li>
 *     <li>{@code Schematic(Filename:regex,Format:…,Origin:…,PasteMethod:…,FillBias:…,Rotation:…)} ({@code Sc})</li>
 *     <li>{@code Shape(Shape:forma,Pattern:patrón)} ({@code Sh})</li>
 *     <li>{@code Expression(Expression:=fórmula,Palette:paleta)} ({@code Ex})</li>
 *     <li>{@code TexturedShape(Shape:forma,TexturingShape:forma,Palette:paleta)} ({@code TS})</li>
 *     <li>{@code Icosphere(Pattern:…,Randomness:0.5,Subdivisions:0,Platform:0)} ({@code Ic})</li>
 * </ul>
 *
 * <p>Todas admiten {@code RelativeOffset} ({@code RO}), {@code WorldOffset} ({@code WO}) y {@code OffsetJitter}
 * ({@code OJ}); las procedurales, además, {@code Origin:CENTER|BOTTOM} (propio de KomaEdit).</p>
 */
public record StructureSpec(
        Type type,
        String pattern,
        String palette,
        ProceduralShapes.Spec shape,
        ProceduralShapes.Spec texture,
        ProceduralStructure.IcosphereSettings icosphere,
        boolean centered,
        boolean smooth,
        double fillBias,
        double rotation,
        String filename,
        String format,
        PlacementBuilder.Offsets offsets,
        String source
) {
    public enum Type {
        CLIPBOARD, SCHEMATIC, SHAPE, EXPRESSION, TEXTURED_SHAPE, ICOSPHERE
    }

    private static final String[] COMMON = {"relativeoffset", "ro", "worldoffset", "wo", "offsetjitter", "oj"};

    public static StructureSpec parse(String input) {
        ParamText.Call call = ParamText.call(input);
        String name = ParamText.normalize(call.name());
        PlacementBuilder.Offsets offsets = offsets(call);
        return switch (name) {
            case "clipboard", "cl", "portapapeles" -> {
                allow(call, "origin", "o", "pastemethod", "pm", "fillbias", "fb", "rotation", "r");
                yield new StructureSpec(Type.CLIPBOARD, null, null, null, null, null, centered(call, false),
                        smooth(call), fillBias(call), rotation(call), null, null, offsets, call.source());
            }
            case "schematic", "sc", "esquema" -> {
                allow(call, "filename", "n", "name", "nombre", "format", "f", "origin", "o", "pastemethod", "pm",
                        "fillbias", "fb", "rotation", "r");
                String filename = call.get("filename", "n", "name", "nombre");
                if (filename == null) {
                    throw new IllegalArgumentException("Schematic necesita el nombre: Sc(N:arbol_.*)");
                }
                yield new StructureSpec(Type.SCHEMATIC, null, null, null, null, null, centered(call, false),
                        smooth(call), fillBias(call), rotation(call), filename, call.get("format", "f"), offsets,
                        call.source());
            }
            case "shape", "sh", "forma" -> {
                allow(call, "shape", "s", "forma", "pattern", "p", "patron", "origin", "o");
                String shape = require(call, "Shape necesita S: y P:, por ejemplo Sh(S:Cone,P:stone)",
                        "shape", "s", "forma");
                String pattern = require(call, "Shape necesita el patrón P:, por ejemplo Sh(S:Cone,P:stone)",
                        "pattern", "p", "patron");
                yield new StructureSpec(Type.SHAPE, pattern, null, ProceduralShapes.parse(shape), null, null,
                        centered(call, true), false, 1.0, 0.0, null, null, offsets, call.source());
            }
            case "expression", "ex", "expresion" -> {
                allow(call, "expression", "e", "expresion", "palette", "p", "paleta", "origin", "o");
                String expression = require(call, "Expression necesita E: y P:, por ejemplo Ex(E:=y*.5+.5,P:##Magma)",
                        "expression", "e", "expresion");
                String palette = require(call, "Expression necesita la paleta P:", "palette", "p", "paleta");
                String formula = expression.startsWith("=") ? expression.substring(1) : expression;
                yield new StructureSpec(Type.EXPRESSION, null, palette, ProceduralShapes.formula(formula), null,
                        null, centered(call, true), false, 1.0, 0.0, null, null, offsets, call.source());
            }
            case "texturedshape", "ts", "formatexturizada" -> {
                allow(call, "shape", "s", "forma", "texturingshape", "t", "textura", "palette", "p", "paleta",
                        "origin", "o");
                String shape = require(call, "TexturedShape necesita S:, T: y P:", "shape", "s", "forma");
                String texture = require(call, "TexturedShape necesita T: (la forma o fórmula que reparte la paleta)",
                        "texturingshape", "t", "textura");
                String palette = require(call, "TexturedShape necesita la paleta P:", "palette", "p", "paleta");
                yield new StructureSpec(Type.TEXTURED_SHAPE, null, palette, ProceduralShapes.parse(shape),
                        ProceduralShapes.parse(texture), null, centered(call, true), false, 1.0, 0.0, null, null,
                        offsets, call.source());
            }
            case "icosphere", "ic", "icosfera", "roca" -> {
                allow(call, "pattern", "p", "patron", "randomness", "r", "aleatoriedad", "subdivisions", "s",
                        "subdivisiones", "platform", "pl", "plataforma", "origin", "o");
                String pattern = require(call, "Icosphere necesita el patrón P:, por ejemplo Ic(P:stone)",
                        "pattern", "p", "patron");
                String randomness = call.get("randomness", "r", "aleatoriedad");
                String subdivisions = call.get("subdivisions", "s", "subdivisiones");
                String platform = call.get("platform", "pl", "plataforma");
                ProceduralStructure.IcosphereSettings settings = new ProceduralStructure.IcosphereSettings(
                        randomness == null ? 0.5 : ParamText.number(randomness, 0, 1, "Randomness"),
                        subdivisions == null ? 0 : (int) ParamText.number(subdivisions, 0,
                                Icosphere.MAX_SUBDIVISIONS, "Subdivisions"),
                        platform == null ? 0.0 : ParamText.number(platform, 0, 1, "Platform")
                );
                yield new StructureSpec(Type.ICOSPHERE, pattern, null, null, null, settings, centered(call, true),
                        false, 1.0, 0.0, null, null, offsets, call.source());
            }
            default -> throw new IllegalArgumentException("Estructura desconocida: " + call.name() + " (Clipboard, "
                    + "Schematic, Shape, Expression, TexturedShape o Icosphere)");
        };
    }

    /** Crea la estructura procedural cuando ya se sabe cuántos bloques tiene la paleta. */
    public ProceduralStructure procedural(int paletteSize) {
        boolean bottom = !centered;
        return switch (type) {
            case SHAPE -> ProceduralStructure.shape(shape, bottom, source);
            case EXPRESSION -> ProceduralStructure.expression(shape, paletteSize, bottom, source);
            case TEXTURED_SHAPE -> ProceduralStructure.textured(shape, texture, paletteSize, bottom, source);
            case ICOSPHERE -> ProceduralStructure.icosphere(icosphere, bottom, source);
            default -> throw new IllegalStateException(type + " no es procedural");
        };
    }

    public boolean procedural() {
        return type != Type.CLIPBOARD && type != Type.SCHEMATIC;
    }

    private static void allow(ParamText.Call call, String... keys) {
        String[] all = new String[keys.length + COMMON.length];
        System.arraycopy(keys, 0, all, 0, keys.length);
        System.arraycopy(COMMON, 0, all, keys.length, COMMON.length);
        call.requireOnly(all);
    }

    private static String require(ParamText.Call call, String message, String... keys) {
        String value = call.get(keys);
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    /** {@code INHERENT} (donde se copió) o {@code CENTER} en bloques; {@code CENTER} o {@code BOTTOM} en formas. */
    private static boolean centered(ParamText.Call call, boolean fallback) {
        String value = call.get("origin", "o");
        if (value == null) {
            return fallback;
        }
        return switch (ParamText.normalize(value)) {
            case "center", "c", "centro" -> true;
            case "inherent", "i", "original" -> {
                if (fallback) {
                    throw new IllegalArgumentException("Las formas no tienen origen INHERENT: usa CENTER o BOTTOM");
                }
                yield false;
            }
            case "bottom", "b", "base", "abajo" -> {
                if (!fallback) {
                    throw new IllegalArgumentException("Origin:BOTTOM es de las formas; en bloques usa INHERENT o CENTER");
                }
                yield false;
            }
            default -> throw new IllegalArgumentException("Origen desconocido: " + value);
        };
    }

    private static boolean smooth(ParamText.Call call) {
        String value = call.get("pastemethod", "pm");
        if (value == null) {
            return false;
        }
        return switch (ParamText.normalize(value)) {
            case "fast", "rapido" -> false;
            case "smooth", "smoothed", "suave", "suavizado" -> true;
            default -> throw new IllegalArgumentException("PasteMethod desconocido: " + value + " (FAST o SMOOTHED)");
        };
    }

    private static double fillBias(ParamText.Call call) {
        String value = call.get("fillbias", "fb");
        return value == null ? 1.0 : ParamText.number(value, 0.05, 8.0, "FillBias");
    }

    private static double rotation(ParamText.Call call) {
        String value = call.get("rotation", "r");
        return value == null ? 0.0 : ParamText.number(value, -3_600, 3_600, "Rotation");
    }

    private static PlacementBuilder.Offsets offsets(ParamText.Call call) {
        String relative = call.get("relativeoffset", "ro");
        String world = call.get("worldoffset", "wo");
        String jitter = call.get("offsetjitter", "oj");
        if (relative == null && world == null && jitter == null) {
            return PlacementBuilder.Offsets.NONE;
        }
        return new PlacementBuilder.Offsets(
                relative == null ? new Vec3(0, 0, 0) : vector(relative, "RelativeOffset"),
                world == null ? new Vec3(0, 0, 0) : vector(world, "WorldOffset"),
                jitter == null ? 0.0 : ParamText.number(jitter, 0, 1, "OffsetJitter")
        );
    }

    private static Vec3 vector(String value, String name) {
        String text = value.trim();
        if (text.startsWith("(") && text.endsWith(")")) {
            text = text.substring(1, text.length() - 1);
        }
        String[] parts = text.split(",");
        if (parts.length != 3) {
            throw new IllegalArgumentException(name + " espera (x,y,z)");
        }
        return new Vec3(ParamText.number(parts[0], -4_096, 4_096, name),
                ParamText.number(parts[1], -4_096, 4_096, name),
                ParamText.number(parts[2], -4_096, 4_096, name));
    }
}
