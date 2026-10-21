package com.komainu.moxia;

import com.google.gson.JsonParser;
import com.komainu.moxia.permission.PermissionNodes;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La documentación sigue entera después de fusionar ramas hechas en paralelo: los merges de texto no avisan cuando
 * repiten una sección, la parten o dejan un bloque de código sin cerrar (y entonces todo lo que viene detrás se ve al
 * revés en GitHub). Comprueba {@code docs/MOD_DOCUMENTATION.md}: el índice, las secciones, los bloques de código y la
 * tabla de paquetes de permisos.
 */
class DocumentationStructureTest {
    private static final Path DOCUMENTATION = Path.of("docs", "MOD_DOCUMENTATION.md");
    private static final Pattern HEADING = Pattern.compile("^(#{2,6}) (.+)$");
    private static final Pattern LINK = Pattern.compile("\\]\\(#([^)]+)\\)");

    private static List<String> lines;

    @BeforeAll
    static void read() throws IOException {
        lines = Files.readAllLines(DOCUMENTATION, StandardCharsets.UTF_8);
    }

    @Test
    void codeBlocksAreClosedAndTheirJsonReads() {
        List<String> wrong = new ArrayList<>();
        boolean inside = false;
        String language = "";
        StringBuilder block = new StringBuilder();
        int opened = 0;
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index).strip();
            if (!line.startsWith("```")) {
                if (inside) {
                    block.append(lines.get(index)).append('\n');
                }
                continue;
            }
            if (!inside) {
                inside = true;
                opened = index + 1;
                language = line.substring(3).strip();
                block.setLength(0);
            } else {
                if (!line.equals("```")) {
                    wrong.add("línea " + (index + 1) + ": se abre un bloque dentro del de la línea " + opened);
                }
                if (language.equals("json")) {
                    try {
                        JsonParser.parseString(block.toString());
                    } catch (RuntimeException exception) {
                        wrong.add("línea " + opened + ": el JSON no se lee (" + exception.getMessage() + ")");
                    }
                }
                inside = false;
            }
        }
        if (inside) {
            wrong.add("línea " + opened + ": el bloque no se cierra");
        }
        assertEquals(List.of(), wrong, "bloques de código rotos");
    }

    @Test
    void theIndexListsEverySectionOnce() {
        int start = lines.indexOf("## Índice");
        assertTrue(start >= 0, "falta el índice");
        Set<String> anchors = anchors();
        List<String> links = new ArrayList<>();
        Set<String> groups = new HashSet<>();
        List<String> repeatedGroups = new ArrayList<>();
        int index = start + 1;
        for (; index < lines.size() && !lines.get(index).startsWith("## "); index++) {
            String line = lines.get(index);
            Matcher group = Pattern.compile("^- \\*\\*(.+?)\\*\\*").matcher(line);
            if (group.find() && !groups.add(group.group(1))) {
                repeatedGroups.add(group.group(1));
            }
            Matcher link = LINK.matcher(line);
            while (link.find()) {
                links.add(link.group(1));
            }
        }
        assertEquals(List.of(), repeatedGroups, "grupos del índice repetidos");
        List<String> broken = links.stream().filter(link -> !anchors.contains(link)).toList();
        assertEquals(List.of(), broken, "enlaces del índice que no llevan a ninguna sección");
        Set<String> seen = new HashSet<>();
        List<String> twice = links.stream().filter(link -> !seen.add(link)).toList();
        assertEquals(List.of(), twice, "secciones enlazadas dos veces en el índice");
        List<String> missing = new ArrayList<>();
        for (; index < lines.size(); index++) {
            Matcher heading = HEADING.matcher(lines.get(index));
            if (heading.matches() && heading.group(1).length() == 2 && !links.contains(slug(heading.group(2)))) {
                missing.add(heading.group(2));
            }
        }
        assertEquals(List.of(), missing, "secciones que no están en el índice");
    }

    @Test
    void noSectionRepeatsAHeading() {
        List<String> repeated = new ArrayList<>();
        String section = "";
        Set<String> headings = new HashSet<>();
        Set<String> sections = new HashSet<>();
        for (String line : outsideCode()) {
            Matcher heading = HEADING.matcher(line);
            if (!heading.matches()) {
                continue;
            }
            if (heading.group(1).length() == 2) {
                section = heading.group(2);
                headings.clear();
                if (!sections.add(section)) {
                    repeated.add(section);
                }
            } else if (!headings.add(heading.group(1) + heading.group(2))) {
                repeated.add(section + " › " + heading.group(2));
            }
        }
        assertEquals(List.of(), repeated, "títulos repetidos dentro de una misma sección");
    }

    @Test
    void thePermissionPackTableHasOneRowPerPack() {
        Map<String, Integer> rows = new HashMap<>();
        for (String line : lines) {
            for (PermissionNodes.Pack pack : PermissionNodes.Pack.values()) {
                if (line.startsWith("| `" + pack.node() + "` |")) {
                    rows.merge(pack.node(), 1, Integer::sum);
                }
            }
        }
        Map<String, Integer> expected = new HashMap<>();
        for (PermissionNodes.Pack pack : PermissionNodes.Pack.values()) {
            expected.put(pack.node(), 1);
        }
        assertEquals(expected, rows, "filas de cada paquete en la tabla de permisos");
    }

    /** Dentro de una misma sección; en dos secciones, una frase como «si no se puede leer…» puede valer para las dos. */
    @Test
    void noBulletIsRepeatedWordForWordInASection() {
        Set<String> seen = new LinkedHashSet<>();
        List<String> repeated = new ArrayList<>();
        for (String line : outsideCode()) {
            if (line.startsWith("## ")) {
                seen.clear();
            }
            String bullet = line.strip();
            if (bullet.startsWith("- ") && bullet.length() > 60 && !seen.add(bullet)) {
                repeated.add(bullet.substring(0, 60) + "…");
            }
        }
        assertEquals(List.of(), repeated, "viñetas repetidas palabra por palabra en una sección");
    }

    /** Las anclas de GitHub de cada título, con el sufijo {@code -1}, {@code -2}… de los repetidos. */
    private static Set<String> anchors() {
        Set<String> anchors = new HashSet<>();
        Map<String, Integer> counts = new HashMap<>();
        for (String line : outsideCode()) {
            Matcher heading = HEADING.matcher(line);
            if (heading.matches()) {
                String slug = slug(heading.group(2));
                int count = counts.merge(slug, 1, Integer::sum) - 1;
                anchors.add(count == 0 ? slug : slug + "-" + count);
            }
        }
        return anchors;
    }

    /** El ancla de GitHub: minúsculas, sin signos (salvo guiones y letras con tilde) y los espacios como guiones. */
    static String slug(String heading) {
        String lower = Normalizer.normalize(heading.strip().toLowerCase(Locale.ROOT), Normalizer.Form.NFC);
        return lower.replaceAll("[^\\p{L}\\p{N}\\- _]", "").replace(' ', '-');
    }

    private static List<String> outsideCode() {
        List<String> prose = new ArrayList<>();
        boolean inside = false;
        for (String line : lines) {
            if (line.strip().startsWith("```")) {
                inside = !inside;
            } else if (!inside) {
                prose.add(line);
            }
        }
        return prose;
    }
}
