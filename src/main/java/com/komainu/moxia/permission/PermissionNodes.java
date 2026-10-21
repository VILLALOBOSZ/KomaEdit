package com.komainu.moxia.permission;

import com.komainu.moxia.blueprint.BlueprintPermissions;
import com.komainu.moxia.capability.BuildCapability;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Permisos finos de KomaEdit, uno por orden o modo, y los paquetes que los agrupan, como la página de permisos de
 * ezEdits. Es lógica pura: recibe qué nodos tiene el jugador y dice si puede usar una función.
 *
 * <p>Un nodo se concede si el jugador lo tiene, si tiene alguno de sus padres ({@code komaedit.build.deform} cubre
 * {@code komaedit.build.deform.noise}), si tiene un paquete que lo incluye ({@code komaedit.permpack.basic},
 * {@code intermediate}, {@code advanced} o {@code admin}) o si tiene el permiso amplio de siempre de esa función
 * ({@code komaedit.build}, {@code komaedit.select}, {@code komaedit.use}…). Lo último mantiene intactas las
 * instalaciones que ya usan los permisos amplios: los nodos finos solo sirven para dar menos.</p>
 */
public final class PermissionNodes {
    public static final String PACK_PREFIX = "komaedit.permpack.";
    private static final Map<String, Node> NODES = new LinkedHashMap<>();

    /** Paquetes de permisos. Básico, intermedio y avanzado se incluyen uno en otro; admin va aparte. */
    public enum Pack {
        BASIC("basic", "Básico", 0),
        INTERMEDIATE("intermediate", "Intermedio", 1),
        ADVANCED("advanced", "Avanzado", 2),
        ADMIN("admin", "Admin", -1);

        private final String id;
        private final String displayName;
        private final int rank;

        Pack(String id, String displayName, int rank) {
            this.id = id;
            this.displayName = displayName;
            this.rank = rank;
        }

        public String id() {
            return id;
        }

        public String displayName() {
            return displayName;
        }

        public String node() {
            return PACK_PREFIX + id;
        }

        /** ¿Incluye este paquete el nodo? Los nodos de figuras propias de KomaEdit van en el avanzado. */
        public boolean grants(String node) {
            Node registered = NODES.get(node);
            if (registered == null) {
                return this == ADVANCED && node.startsWith(KomaPermissions.BUILD + ".");
            }
            if (this == ADMIN || registered.pack() == ADMIN) {
                return this == registered.pack();
            }
            return registered.pack().rank <= rank;
        }
    }

    /**
     * Un nodo fino.
     *
     * @param legacy   permiso amplio que ya daba acceso antes; quien lo tenga sigue pudiendo
     * @param ezEdits  el nodo equivalente de ezEdits, para quien migre sus permisos (puede ser {@code null})
     */
    public record Node(String node, Pack pack, String legacy, String ezEdits, String description) {
    }

    static {
        // Básico.
        String use = KomaPermissions.USE;
        String build = KomaPermissions.BUILD;
        String select = KomaPermissions.SELECT;
        add(use, Pack.BASIC, null, null, "Acceso a /komaedit, la ayuda y la información");
        add(KomaPermissions.PATH, Pack.BASIC, null, null, "Trayectorias (/komaedit path)");
        add(KomaPermissions.HISTORY, Pack.BASIC, null, null, "Historial del editor");
        for (String shape : List.of("cone", "polydome", "polygon", "square", "tetrahedron", "torus")) {
            add(build + ".shape." + shape, Pack.BASIC, build, "ezedits.gen." + shape,
                    "/komaedit shape " + shape);
        }
        add(build + ".deform.rotate", Pack.BASIC, build, "ezedits.deform.rotate", "/komaedit deform rotate");
        add(build + ".slabmerge", Pack.BASIC, build, "ezedits.region.solidslab", "/komaedit slabmerge");
        for (String tweak : List.of("deinfest", "persist", "waterlog", "wax")) {
            add(build + ".tweak." + tweak, Pack.BASIC, build, "ezedits.survival." + tweak, "/komaedit tweak " + tweak);
        }
        add(build + ".glassgradient", Pack.BASIC, build, "ezedits.stainedglass.gradient", "/komaedit glassgradient");
        add(build + ".inflate", Pack.BASIC, build, "ezedits.smooth.inflate", "/komaedit inflate");
        add(build + ".deflate", Pack.BASIC, build, "ezedits.smooth.deflate", "/komaedit deflate");
        add(select + ".superwand", Pack.BASIC, select, "ezedits.tools.superwand", "/komaedit superwand");
        add("komaedit.analyze", Pack.BASIC, use, null, "/komaedit analyze (lista de materiales)");
        add(build + ".gravity", Pack.BASIC, build, null, "/komaedit gravity");
        add(build + ".snow", Pack.BASIC, build, null, "/komaedit snow");
        add(build + ".drain", Pack.BASIC, build, null, "/komaedit drain");
        add(build + ".waterlog", Pack.BASIC, build, null, "/komaedit waterlog");
        for (String color : List.of("similar", "gradient", "palette", "hotbar")) {
            add("komaedit.color." + color, Pack.BASIC, use, null, "/komaedit color " + color);
        }
        for (String[] entry : new String[][]{{"next", "next"}, {"here", "here"}, {"invert", "invert"},
                {"delpos2", "delpos"}, {"encapsulate", "encapsulate"}, {"encapsulatenear", "encapsulatenear"},
                {"catenary", "catenary"}}) {
            add(select + "." + entry[0], Pack.BASIC, select, "ezedits.selection." + entry[1],
                    "/komaedit sel " + entry[0]);
        }

        // Intermedio.
        add(KomaPermissions.PAINT, Pack.INTERMEDIATE, null, null, "Pintura rápida del editor");
        add(KomaPermissions.CLIPBOARD, Pack.INTERMEDIATE, null, null, "Portapapeles y schematics en las estructuras");
        add(KomaPermissions.RECIPE, Pack.INTERMEDIATE, null, null, "Recetas del Studio");
        for (String[] entry : new String[][]{{"save", "save"}, {"load", "load"}, {"list", "list"},
                {"delete", "delete"}}) {
            add(select + "." + entry[0], Pack.INTERMEDIATE, select, "ezedits.selection." + entry[1],
                    "/komaedit sel " + entry[0]);
        }
        for (String palette : List.of("save", "delete", "list", "print", "fetch", "encode", "decode")) {
            add("komaedit.palette." + palette, Pack.INTERMEDIATE, use,
                    "ezedits.palettes." + palette, "/komaedit palette " + palette);
        }
        add(build + ".palette.place", Pack.INTERMEDIATE, build, "ezedits.palettes.place", "/komaedit palette place");
        add(build + ".palette.swap", Pack.INTERMEDIATE, build, "ezedits.palettes.swap", "/komaedit palette swap");
        for (String noise : List.of("save", "delete", "list", "print")) {
            add("komaedit.noise." + noise, Pack.INTERMEDIATE, use, "ezedits.noise." + noise,
                    "/komaedit noise " + noise);
        }
        for (String brush : List.of("paletteshift", "gradient", "gradientstroke", "petalpainter", "place")) {
            add(build + ".brush." + brush, Pack.INTERMEDIATE, build, "ezedits.brush." + brush,
                    "/komaedit pincel " + brush);
        }
        for (String mode : List.of("hexagonalize", "noise", "voronoialize", "voronoialize2", "voxelize")) {
            add(build + ".deform." + mode, Pack.INTERMEDIATE, build, "ezedits.deform." + mode,
                    "/komaedit deform " + mode);
        }
        add(build + ".deform.cylindrify", Pack.INTERMEDIATE, build, null, "/komaedit deform cylindrify");
        add(build + ".deform.spherify", Pack.INTERMEDIATE, build, null, "/komaedit deform spherify");
        for (String mode : List.of("fuzzify", "rockify", "voronoify", "noisify")) {
            add(build + ".surface." + mode, Pack.INTERMEDIATE, build, "ezedits.surface." + mode,
                    "/komaedit surface " + mode);
        }
        add(build + ".smooth", Pack.INTERMEDIATE, build, "ezedits.smooth.ezsmooth", "/komaedit smooth");
        for (String mode : List.of("advanced", "ambient", "axisgradient", "blend", "blocklight", "cells",
                "curvature", "flow", "noise", "pointlight", "shift", "sunlight")) {
            add(build + ".texture." + mode, Pack.INTERMEDIATE, build,
                    "ezedits.texture." + (mode.equals("flow") ? "surfaceflow" : mode), "/komaedit texture " + mode);
        }
        add(build + ".vines", Pack.INTERMEDIATE, build, "ezedits.region.vines", "/komaedit vines");
        add(build + ".moss", Pack.INTERMEDIATE, build, "ezedits.region.moss", "/komaedit moss");
        add(build + ".statecycle", Pack.INTERMEDIATE, build, "ezedits.region.statecycle", "/komaedit statecycle");
        add(build + ".noisegen", Pack.INTERMEDIATE, build, "ezedits.region.noisegen", "/komaedit noisegen");
        add(build + ".flowfield", Pack.INTERMEDIATE, build, "ezedits.flow.flowfield", "/komaedit flowfield");
        add(build + ".flowline", Pack.INTERMEDIATE, build, "ezedits.flow.flowline", "/komaedit flowline");
        add(build + ".place", Pack.INTERMEDIATE, build, "ezedits.place", "/komaedit place");
        add(build + ".symmetry.reflectional", Pack.INTERMEDIATE, build, "ezedits.symmetry.reflectional",
                "/komaedit symmetry con espejos en X, Z e Y");
        add(build + ".symmetry.rotational", Pack.INTERMEDIATE, build, "ezedits.symmetry.rotational",
                "/komaedit symmetry radial");
        add(build + ".text", Pack.INTERMEDIATE, build, null, "/komaedit text");
        add(build + ".biome", Pack.INTERMEDIATE, build, null, "/komaedit biome");
        add(build + ".brush.biome", Pack.INTERMEDIATE, build, null, "/komaedit pincel biome");
        add(build + ".shade", Pack.INTERMEDIATE, build, null, "/komaedit shade");
        add(build + ".tree", Pack.INTERMEDIATE, build, null, "/komaedit tree");
        add(build + ".rock", Pack.INTERMEDIATE, build, null, "/komaedit rock");
        add(build + ".brush.tree", Pack.INTERMEDIATE, build, null, "/komaedit pincel tree");
        add(build + ".brush.rock", Pack.INTERMEDIATE, build, null, "/komaedit pincel rock");
        add(build + ".brush.heightmap", Pack.INTERMEDIATE, build, null, "/komaedit pincel heightmap");
        add(build + ".brush.paint", Pack.INTERMEDIATE, build, null,
                "/komaedit pincel paint (pintar el terreno con un degradado)");
        add(build + ".brush.sculpt", Pack.INTERMEDIATE, build, null,
                "/komaedit pincel sculpt (esculpir, roca, soldar, fundir, rugosidad, grietas)");
        add(build + ".terrain.ramp", Pack.INTERMEDIATE, build, null, "/komaedit terreno rampa");
        add(build + ".terrain.river", Pack.INTERMEDIATE, build, null, "/komaedit terreno rio|arroyo");
        add(build + ".terrain.canyon", Pack.INTERMEDIATE, build, null, "/komaedit terreno canon|barranco");
        add(build + ".terrain.paint", Pack.INTERMEDIATE, build, null, "/komaedit terreno pintar");
        add(build + ".roof", Pack.INTERMEDIATE, build, null, "/komaedit roof (tejados)");
        add(build + ".loft", Pack.INTERMEDIATE, build, null, "/komaedit loft (superficies entre curvas)");
        add(BlueprintPermissions.LOAD, Pack.INTERMEDIATE, KomaPermissions.CLIPBOARD, null,
                "Biblioteca de planos: ver, buscar y cargar al portapapeles (/komaedit blueprint list|load)");
        add(BlueprintPermissions.SAVE, Pack.INTERMEDIATE, KomaPermissions.CLIPBOARD, null,
                "Guardar, etiquetar y borrar planos propios (/komaedit blueprint save|tags|delete)");
        add(BlueprintPermissions.PLACE, Pack.INTERMEDIATE, build, null, "/komaedit blueprint place");
        add(BlueprintPermissions.STAMP, Pack.INTERMEDIATE, build, null, "/komaedit pincel stamp (estampar planos)");

        // Avanzado.
        add(build, Pack.ADVANCED, null, null, "Todas las figuras y ediciones (también las propias de KomaEdit)");
        add(KomaPermissions.INSTANCE, Pack.ADVANCED, null, null, "Instancias procedurales");
        add(build + ".array", Pack.ADVANCED, build, "ezedits.array", "/komaedit array");
        add(build + ".scatter", Pack.ADVANCED, build, "ezedits.scatter", "/komaedit scatter");
        add(build + ".brush.array", Pack.ADVANCED, build, "ezedits.brush.array", "/komaedit pincel array");
        add(build + ".brush.scatter", Pack.ADVANCED, build, "ezedits.brush.scatter", "/komaedit pincel scatter");
        add(build + ".smoothblocks", Pack.ADVANCED, build, "ezedits.smooth.smoothblocks", "/komaedit smoothblocks");
        add(build + ".symmetry.worldedit", Pack.ADVANCED, build, null,
                "/komaedit symmetry worldedit: refleja también las ediciones de WorldEdit y KomaEdit");
        add(build + ".image", Pack.ADVANCED, build, "ezedits.stainedglass.image", "/komaedit image");
        add(BlueprintPermissions.SHARE, Pack.ADVANCED, null, null,
                "Compartir planos propios con todo el servidor (/komaedit blueprint share)");

        // Capacidades de construcción a mano (menú rápido y /komaedit capability).
        for (BuildCapability capability : BuildCapability.values()) {
            Pack pack = switch (capability) {
                case EXTRUDE, SMEAR, NO_UPDATES, REACH -> Pack.INTERMEDIATE;
                default -> Pack.BASIC;
            };
            add(capability.permission(), pack, build, null, "Capacidad «" + capability.displayName() + "»");
        }
        add(BuildCapability.FLY_SPEED_PERMISSION, Pack.BASIC, build, null, "Velocidad de vuelo del menú rápido");
        add(build + ".terrain.erode.hydraulic", Pack.ADVANCED, build, null, "/komaedit terreno erosion hidraulica");
        add(build + ".terrain.erode.thermal", Pack.ADVANCED, build, null, "/komaedit terreno erosion termica");

        // Administración.
        add(BuildCapability.GAMEMODE_PERMISSION, Pack.ADMIN, null, null, "Cambiar el modo de juego desde el menú rápido");
        add(BuildCapability.ANY_MODE_PERMISSION, Pack.ADMIN, null, null, "Usar las capacidades fuera de creativo");
        add(KomaPermissions.ADMIN_RELOAD, Pack.ADMIN, null, "ezedits.admin.reload", "/komaedit reload");
        add(KomaPermissions.ADMIN_JOBS, Pack.ADMIN, null, null,
                "Ver, pausar y cancelar los trabajos de todos (/komaedit jobs)");
        add(KomaPermissions.ADMIN_STAMP_EXPORT, Pack.ADMIN, null, null,
                "Copiar un sello de serie a config/komaedit/heightmaps para retocarlo"
                        + " (/komaedit terreno sellos exportar)");
        add(KomaPermissions.PROJECT_BACKUP, Pack.ADMIN, null, null, "/komaedit project backup");
        add(KomaPermissions.ADMIN_IMAGE_URL, Pack.ADMIN, null, null,
                "Imágenes por URL en /komaedit image (si urlsRequireAdmin está activo)");
        add(KomaPermissions.LIMIT_UNRESTRICTED, Pack.ADMIN, null, null, "Sin límite de bloques por operación");
        add(BlueprintPermissions.MANAGE, Pack.ADMIN, null, null,
                "Borrar o reemplazar los planos compartidos de otros jugadores");
        add(BlueprintPermissions.UNLIMITED, Pack.ADMIN, null, null,
                "Sin los límites de número y tamaño de planos de blueprints.json");
    }

    private PermissionNodes() {
    }

    private static void add(String node, Pack pack, String legacy, String ezEdits, String description) {
        NODES.put(node, new Node(node, pack, legacy, ezEdits, description));
    }

    public static List<Node> all() {
        return List.copyOf(NODES.values());
    }

    public static Node find(String node) {
        return NODES.get(node);
    }

    /** Nodos registrados bajo un prefijo, por ejemplo {@code komaedit.build.deform}. */
    public static List<Node> family(String prefix) {
        List<Node> nodes = new ArrayList<>();
        for (Node node : NODES.values()) {
            if (node.node().startsWith(prefix + ".")) {
                nodes.add(node);
            }
        }
        return Collections.unmodifiableList(nodes);
    }

    /**
     * ¿Concede {@code held} el nodo? {@code held} dice si el jugador tiene un nodo concreto (como lo dice el
     * gestor de permisos del servidor).
     */
    public static boolean allows(String node, Predicate<String> held) {
        String normalized = node.toLowerCase(Locale.ROOT);
        if (held.test(normalized)) {
            return true;
        }
        int dot = normalized.lastIndexOf('.');
        while (dot > "komaedit".length()) {
            String parent = normalized.substring(0, dot);
            if (held.test(parent)) {
                return true;
            }
            dot = parent.lastIndexOf('.');
        }
        for (Pack pack : Pack.values()) {
            if (pack.grants(normalized) && held.test(pack.node())) {
                return true;
            }
        }
        // El permiso amplio de siempre solo cuenta si el jugador lo tiene de verdad, no por un paquete: si no, el
        // paquete básico (que incluye komaedit.use) daría todo lo que antes colgaba de komaedit.use.
        Node registered = NODES.get(normalized);
        if (registered == null || registered.legacy() == null) {
            return false;
        }
        String legacy = registered.legacy();
        return held.test(legacy) || legacy.equals(KomaPermissions.USE) && held.test(KomaPermissions.LEGACY_USE);
    }

    /** ¿Concede {@code held} algún nodo de la familia (o la familia entera)? Para mostrar u ocultar una orden. */
    public static boolean allowsAny(String prefix, Predicate<String> held) {
        if (allows(prefix, held)) {
            return true;
        }
        for (Node node : family(prefix)) {
            if (allows(node.node(), held)) {
                return true;
            }
        }
        return false;
    }

    /** ¿Tiene algún paquete? Un paquete da acceso a {@code /komaedit}, como {@code komaedit.use}. */
    public static boolean anyPack(Predicate<String> held) {
        for (Pack pack : Pack.values()) {
            if (held.test(pack.node())) {
                return true;
            }
        }
        return false;
    }
}
