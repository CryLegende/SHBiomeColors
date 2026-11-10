package org.Cry_Legende.sHBiomeColors;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.Cry_Legende.sHBiomeColors.network.NetworkLink;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Mode réseau : les biomes créés ou modifiés sur un serveur sont donnés aux autres, qui les ajoutent à leur
 * biomes.yml et à leur datapack sans toucher à aucune zone. Un biome reçu ne repart jamais sur le bus.
 *
 * <p>Le bus n'est pas durable. Au démarrage, {@link #hello()} annonce nos biomes : les serveurs qui tournent
 * nous envoient ceux qui nous manquent. Un biome présent partout mais avec d'autres couleurs n'est jamais
 * écrasé, il est seulement signalé dans les logs.</p>
 */
final class BiomeSync {

    private static final Gson GSON = new Gson();
    // Biomes par message : un serveur qui a tout raté ne reçoit pas un seul message énorme
    private static final int BATCH = 50;

    private final Logger log;
    private final BiomeStore store;
    private final NetworkLink link;

    BiomeSync(Logger log, BiomeStore store, NetworkLink link) {
        this.log = log;
        this.store = store;
        this.link = link;
    }

    void start() {
        link.subscribe(this::receive);
    }

    /**
     * À appeler une fois le biome enregistré (biomes.yml et datapack) : le donne aux autres serveurs.
     */
    void publish(CustomBiome biome) {
        send(List.of(biome));
    }

    /**
     * Annonce nos biomes (id et couleurs) aux autres serveurs, qui nous envoient ceux qu'il nous manque.
     */
    void hello() {
        sendHello(false);
    }

    // ------------------------------------------------------------------ envoi

    private void send(Collection<CustomBiome> biomes) {
        JsonArray array = new JsonArray();
        biomes.forEach(biome -> array.add(GSON.toJsonTree(biome.toYaml())));
        JsonObject root = new JsonObject();
        root.addProperty("type", "biomes");
        root.add("biomes", array);
        link.publish(root.toString());
    }

    private void sendHello(boolean reply) {
        JsonObject list = new JsonObject();
        store.all().forEach(biome -> list.addProperty(biome.id, biome.signature()));
        JsonObject root = new JsonObject();
        root.addProperty("type", "hello");
        root.addProperty("reponse", reply);
        root.add("biomes", list);
        link.publish(root.toString());
    }

    // ------------------------------------------------------------------ réception (fil principal)

    void receive(String origin, String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            switch (root.get("type").getAsString()) {
                case "biomes" -> receiveBiomes(origin, root.getAsJsonArray("biomes"));
                case "hello" -> receiveHello(origin, root.getAsJsonObject("biomes"),
                    root.has("reponse") && root.get("reponse").getAsBoolean());
                default -> log.warning("Message de type inconnu reçu de " + origin + " : ignoré.");
            }
        } catch (RuntimeException e) {
            log.warning("Message illisible reçu de " + origin + " : " + e);
        }
    }

    private void receiveBiomes(String origin, JsonArray array) {
        List<String> warnings = new ArrayList<>();
        boolean changed = false;
        for (JsonElement element : array) {
            Map<String, Object> map = new LinkedHashMap<>();
            element.getAsJsonObject().entrySet().forEach(entry -> map.put(entry.getKey(), entry.getValue().getAsString()));
            CustomBiome incoming = CustomBiome.fromYaml(map, warnings);
            // L'id sert de chemin dans le datapack : on ne laisse pas passer un message venu de l'extérieur avec ".."
            if (incoming == null || incoming.id.contains("..")) {
                warnings.add("Biome sans id valide reçu de " + origin + " : ignoré (" + map.get("id") + ").");
                continue;
            }
            CustomBiome local = store.get(incoming.id);
            if (local != null && local.sameColors(incoming)) {
                continue;
            }
            store.put(incoming);
            changed = true;
            log.info("Biome " + incoming.id + " reçu de " + origin + " : actif au prochain redémarrage.");
        }
        warnings.forEach(log::warning);
        if (changed) {
            try {
                store.save();
                store.writeDatapack();
            } catch (IOException e) {
                log.severe("Sauvegarde des biomes reçus de " + origin + " impossible : " + e.getMessage());
            }
        }
    }

    private void receiveHello(String origin, JsonObject theirs, boolean reply) {
        List<CustomBiome> missing = new ArrayList<>();
        for (CustomBiome biome : store.all()) {
            JsonElement signature = theirs.get(biome.id);
            if (signature == null) {
                missing.add(biome);
            } else if (!signature.getAsString().equals(biome.signature())) {
                log.warning("Biome " + biome.id + " différent sur " + origin + " : refaites la commande de couleur pour aligner les serveurs.");
            }
        }
        for (int i = 0; i < missing.size(); i += BATCH) {
            send(missing.subList(i, Math.min(missing.size(), i + BATCH)));
        }
        if (!missing.isEmpty()) {
            log.info(missing.size() + " biomes envoyés à " + origin + " (il ne les avait pas).");
        }
        // Ils en ont que nous n'avons pas : on annonce les nôtres pour qu'ils nous les envoient (une seule réponse, pas de boucle)
        if (!reply && theirs.keySet().stream().anyMatch(id -> store.get(id) == null)) {
            sendHello(true);
        }
    }
}
