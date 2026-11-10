package org.Cry_Legende.sHBiomeColors;

import org.Cry_Legende.sHBiomeColors.CustomBiome.ColorType;
import org.Cry_Legende.sHBiomeColors.network.NetworkLink;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deux serveurs reliés par un faux bus : protège l'absence d'écho, l'idempotence et le rattrapage au démarrage.
 */
class BiomeSyncTest {

    @TempDir
    Path dir;

    private final List<String> bus = new ArrayList<>();
    private Server a;
    private Server b;

    /** Un serveur : son dossier, ses biomes, son lien vers l'autre. */
    private final class Server implements NetworkLink {
        final String name;
        final BiomeStore store;
        final BiomeSync sync;
        Server other;
        BiConsumer<String, String> handler;
        int published;

        Server(String name) throws IOException {
            this.name = name;
            this.store = new BiomeStore(dir.resolve(name).resolve("SHBiomeColors"));
            store.load();
            this.sync = new BiomeSync(Logger.getLogger("test-" + name), store, this);
            sync.start();
        }

        @Override
        public void publish(String json) {
            published++;
            bus.add(json);
            other.handler.accept(name, json);
        }

        @Override
        public void subscribe(BiConsumer<String, String> handler) {
            this.handler = handler;
        }

        boolean datapackWritten() {
            return Files.exists(store.packDir().resolve("pack.mcmeta"));
        }
    }

    private static CustomBiome biome(String id, int grass) {
        CustomBiome biome = new CustomBiome(id);
        biome.set(ColorType.GRASS, grass);
        return biome;
    }

    @BeforeEach
    void servers() throws IOException {
        a = new Server("poc1");
        b = new Server("poc2");
        a.other = b;
        b.other = a;
    }

    @Test
    void biomePublishedIsAppliedOnTheOtherServerWithoutEcho() throws IOException {
        CustomBiome rouge = biome("cbc:rouge", 0xFF0000);
        a.store.put(rouge);
        a.sync.publish(rouge);

        CustomBiome received = b.store.get("cbc:rouge");
        assertNotNull(received);
        assertTrue(received.sameColors(rouge));
        assertTrue(b.datapackWritten());
        assertTrue(Files.exists(b.store.packDir().resolve("data/cbc/worldgen/biome/rouge.json")));
        assertEquals(1, a.published);
        assertEquals(0, b.published);
        assertEquals(1, bus.size());
    }

    @Test
    void identicalBiomeChangesNothing() {
        b.store.put(biome("cbc:rouge", 0xFF0000));
        a.sync.publish(biome("cbc:rouge", 0xFF0000));

        assertFalse(b.datapackWritten());
        assertEquals(0, b.published);
    }

    @Test
    void changedBiomeReplacesTheLocalOne() {
        b.store.put(biome("cbc:rouge", 0xFF0000));
        a.sync.publish(biome("cbc:rouge", 0x00FF00));

        assertEquals(0x00FF00, b.store.get("cbc:rouge").get(ColorType.GRASS));
        assertTrue(b.datapackWritten());
    }

    @Test
    void helloGivesTheStartingServerWhatItMissesAndKeepsDifferentBiomes() {
        a.store.put(biome("cbc:rouge", 0xFF0000));
        a.store.put(biome("cbc:vert", 0x00FF00));
        b.store.put(biome("cbc:rouge", 0x111111));

        b.sync.hello();

        assertNotNull(b.store.get("cbc:vert"));
        assertEquals(0x111111, b.store.get("cbc:rouge").get(ColorType.GRASS));
        assertEquals(0xFF0000, a.store.get("cbc:rouge").get(ColorType.GRASS));
    }

    @Test
    void helloAlsoBringsBackWhatOnlyTheStartingServerHas() {
        a.store.put(biome("cbc:vert", 0x00FF00));
        b.store.put(biome("cbc:bleu", 0x0000FF));

        b.sync.hello();

        assertNotNull(a.store.get("cbc:bleu"));
        assertNotNull(b.store.get("cbc:vert"));
        // hello, réponse (hello), puis un envoi de biomes de chaque côté : la conversation s'arrête là
        assertTrue(bus.size() <= 4, "messages échangés : " + bus.size());
    }

    @Test
    void identicalServersSayNothingMoreThanHello() {
        a.store.put(biome("cbc:vert", 0x00FF00));
        b.store.put(biome("cbc:vert", 0x00FF00));

        b.sync.hello();

        assertEquals(1, bus.size());
        assertFalse(a.datapackWritten());
    }

    @Test
    void invalidMessagesAreIgnored() {
        a.sync.receive("poc2", "pas du json");
        a.sync.receive("poc2", "{\"type\":\"inconnu\"}");
        a.sync.receive("poc2", "{\"type\":\"biomes\",\"biomes\":[{\"id\":\"cbc:../../evil\"},{\"herbe\":\"#ffffff\"}]}");

        assertEquals(0, a.store.all().size());
        assertNull(a.store.get("cbc:../../evil"));
        assertFalse(a.datapackWritten());
    }
}
