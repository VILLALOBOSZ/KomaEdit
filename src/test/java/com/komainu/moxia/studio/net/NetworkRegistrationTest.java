package com.komainu.moxia.studio.net;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Los paquetes de red, leídos en las fuentes: cada uno registrado una sola vez y en un sentido, y con quien lo recibe
 * al otro lado. Un paquete del cliente sin registrar no da ningún error: {@code canSend} dice que no y el cliente se
 * lo calla (pasó con {@link StudioAimPayload}, y el clic del Studio en el mundo no le llegaba al servidor).
 */
class NetworkRegistrationTest {
    private static final List<Path> SOURCES = List.of(Path.of("src", "main", "java"), Path.of("src", "client", "java"));
    private static final Pattern DECLARED = Pattern.compile("record (\\w+)\\([^)]*\\)\\s*implements CustomPayload");
    private static final Pattern C2S = Pattern.compile("playC2S\\(\\)\\.register\\(\\s*(?:\\w+\\.)*?(\\w+)\\.ID");
    private static final Pattern S2C = Pattern.compile("playS2C\\(\\)\\.register\\(\\s*(?:\\w+\\.)*?(\\w+)\\.ID");
    private static final Pattern SERVER = Pattern.compile(
            "ServerPlayNetworking\\.registerGlobalReceiver\\(\\s*(?:\\w+\\.)*?(\\w+)\\.ID");
    private static final Pattern CLIENT = Pattern.compile(
            "ClientPlayNetworking\\.registerGlobalReceiver\\(\\s*(?:\\w+\\.)*?(\\w+)\\.ID");

    @Test
    void everyPayloadIsRegisteredOnceAndHasAReceiver() throws IOException {
        String code = sources();
        List<String> toServer = all(C2S, code);
        List<String> toClient = all(S2C, code);
        List<String> serverReceivers = all(SERVER, code);
        List<String> clientReceivers = all(CLIENT, code);

        assertEquals(new TreeSet<>(toServer).size(), toServer.size(), "paquete del cliente registrado dos veces: "
                + toServer);
        assertEquals(new TreeSet<>(toClient).size(), toClient.size(), "paquete del servidor registrado dos veces: "
                + toClient);
        assertEquals(new TreeSet<>(serverReceivers).size(), serverReceivers.size(), "dos receptores en el servidor: "
                + serverReceivers);
        assertEquals(new TreeSet<>(clientReceivers).size(), clientReceivers.size(), "dos receptores en el cliente: "
                + clientReceivers);

        assertEquals(new TreeSet<>(toServer), new TreeSet<>(serverReceivers),
                "cada paquete del cliente necesita su receptor en el servidor (y al revés)");
        assertEquals(new TreeSet<>(toClient), new TreeSet<>(clientReceivers),
                "cada paquete del servidor necesita su receptor en el cliente (y al revés)");

        Set<String> registered = new TreeSet<>(toServer);
        registered.addAll(toClient);
        Set<String> declared = new TreeSet<>(all(DECLARED, code));
        assertTrue(declared.size() > 20, "se encontraron " + declared.size() + " paquetes");
        assertEquals(declared, registered, "cada paquete declarado se registra en un sentido");
        Set<String> both = new TreeSet<>(toServer);
        both.retainAll(toClient);
        assertTrue(both.isEmpty(), "registrados en los dos sentidos: " + both);
    }

    private static List<String> all(Pattern pattern, String code) {
        List<String> found = new ArrayList<>();
        Matcher matcher = pattern.matcher(code);
        while (matcher.find()) {
            found.add(matcher.group(1));
        }
        return found;
    }

    private static String sources() throws IOException {
        StringBuilder code = new StringBuilder();
        for (Path root : SOURCES) {
            try (Stream<Path> files = Files.walk(root)) {
                for (Path file : files.filter(path -> path.toString().endsWith(".java")).sorted().toList()) {
                    code.append(Files.readString(file)).append('\n');
                }
            }
        }
        return code.toString();
    }
}
