package com.komainu.moxia.performance;

import net.minecraft.server.MinecraftServer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Los servicios ligeros comparten la mitad del presupuesto cuando hay ediciones pesadas, y cada uno pide su plazo con
 * {@link ServerWork#deadline}: el que va primero puede gastarla entera. Si el orden fuera siempre el mismo, el
 * fantasma de un objeto enorme (el primero en apuntarse) dejaría sin tiempo durante segundos la foto de los pinceles y
 * la pintura de todos los demás.
 */
class ServerWorkTest {
    @Test
    void everyLightServiceGoesFirstInItsTurn() {
        String[] first = {null};
        List<Consumer<MinecraftServer>> mine = new ArrayList<>();
        for (String name : List.of("fantasma", "pintura", "foto")) {
            Consumer<MinecraftServer> ticker = server -> {
                if (first[0] == null) {
                    first[0] = name;
                }
            };
            mine.add(ticker);
            ServerWork.light(ticker);
        }
        Set<String> firsts = new LinkedHashSet<>();
        try {
            for (int tick = 0; tick < 30; tick++) {
                first[0] = null;
                ServerWork.tick(null);
                firsts.add(first[0]);
            }
        } finally {
            mine.forEach(ServerWork::forgetLight);
        }
        assertEquals(Set.of("fantasma", "pintura", "foto"), firsts, "los que van detrás nunca empiezan el tick");
    }

    @Test
    void turnsStartSomewhereElseEachTimeAndKeepEveryone() {
        assertEquals(List.of("b", "c", "a"), Turns.from(List.of("a", "b", "c"), 1));
        assertEquals(List.of("a", "b", "c"), Turns.from(List.of("a", "b", "c"), 3));
        assertEquals(List.of("c", "a", "b"), Turns.from(List.of("a", "b", "c"), -1));
        assertEquals(List.of(), Turns.from(List.of(), 7));
    }
}
