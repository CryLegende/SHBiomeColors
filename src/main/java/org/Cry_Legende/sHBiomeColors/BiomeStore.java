package org.Cry_Legende.sHBiomeColors;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.Cry_Legende.sHBiomeColors.CustomBiome.ColorType;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * biomes.yml, zones en attente et génération du datapack. Sert au bootstrap (avant les mondes) et au plugin.
 */
final class BiomeStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String[] SPAWN_GROUPS = {"ambient", "axolotls", "creature", "misc", "monster",
        "underground_water_creature", "water_ambient", "water_creature"};

    private final Path dataDir;
    private final Map<String, CustomBiome> biomes = new LinkedHashMap<>();
    private final List<Map<String, Object>> pending = new ArrayList<>();
    final List<String> warnings = new ArrayList<>();
    // Faux tant qu'une lecture n'a pas abouti : on ne réécrit pas un fichier qu'on n'a pas pu lire
    private boolean loaded;

    BiomeStore(Path dataDir) {
        this.dataDir = dataDir;
    }

    Path packDir() {
        return dataDir.resolve("datapack");
    }

    /**
     * Charge biomes.yml et en-attente.yml ; au premier démarrage, importe plugins/CustomBiomeColors/data.json.
     */
    int load() throws IOException {
        Files.createDirectories(dataDir);
        Path file = dataDir.resolve("biomes.yml");
        Path pendingFile = dataDir.resolve("en-attente.yml");
        // Lecture stricte avant de toucher à l'état : un YAML cassé ne doit pas vider les biomes ni le datapack
        YamlConfiguration pendingYaml = Files.exists(pendingFile) ? read(pendingFile) : null;
        YamlConfiguration biomesYaml = Files.exists(file) ? read(file) : null;

        biomes.clear();
        pending.clear();
        warnings.clear();
        int imported = 0;
        if (biomesYaml == null) {
            imported = importLegacy();
            save();
            biomesYaml = read(file);
        }
        for (Map<?, ?> map : biomesYaml.getMapList("biomes")) {
            CustomBiome biome = CustomBiome.fromYaml(map, warnings);
            if (biome == null) {
                warnings.add("Biome sans id valide ignoré : " + map);
            } else {
                biomes.put(biome.id, biome);
            }
        }
        if (pendingYaml != null) {
            for (Map<?, ?> map : pendingYaml.getMapList("zones")) {
                Map<String, Object> zone = new LinkedHashMap<>();
                map.forEach((k, v) -> zone.put(String.valueOf(k), v));
                pending.add(zone);
            }
        }
        loaded = true;
        return imported;
    }

    /**
     * Lit un YAML en échouant s'il est invalide (loadConfiguration rend alors une configuration vide en silence).
     */
    private static YamlConfiguration read(Path file) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file.toFile());
        } catch (InvalidConfigurationException e) {
            throw new IOException(file.getFileName() + " illisible : " + e.getMessage(), e);
        }
        return yaml;
    }

    private void checkWritable(Path file) throws IOException {
        if (!loaded && Files.exists(file)) {
            throw new IOException(file.getFileName() + " n'a pas pu être lu : sauvegarde refusée pour ne pas l'écraser");
        }
    }

    private int importLegacy() throws IOException {
        Path legacy = dataDir.getParent().resolve("CustomBiomeColors").resolve("data.json");
        if (!Files.exists(legacy)) {
            return 0;
        }
        JsonObject root = JsonParser.parseString(Files.readString(legacy)).getAsJsonObject();
        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            if (!CustomBiome.isValidId(entry.getKey())) {
                warnings.add("Biome CustomBiomeColors ignoré (nom invalide) : " + entry.getKey());
                continue;
            }
            JsonArray array = entry.getValue().getAsJsonArray();
            int[] values = new int[array.size()];
            for (int i = 0; i < values.length; i++) {
                values[i] = array.get(i).getAsInt();
            }
            biomes.put(entry.getKey(), CustomBiome.fromLegacy(entry.getKey(), values));
        }
        return biomes.size();
    }

    void save() throws IOException {
        checkWritable(dataDir.resolve("biomes.yml"));
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().setHeader(List.of(
            "Biomes personnalisés. Couleurs en #rrggbb ; herbe et feuillage acceptent aussi 'defaut'.",
            "Modifiables à la main : les changements s'appliquent au prochain redémarrage."));
        yaml.set("biomes", biomes.values().stream().map(CustomBiome::toYaml).toList());
        yaml.save(dataDir.resolve("biomes.yml").toFile());
    }

    void savePending() throws IOException {
        checkWritable(dataDir.resolve("en-attente.yml"));
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("zones", pending);
        yaml.save(dataDir.resolve("en-attente.yml").toFile());
    }

    CustomBiome get(String id) {
        return biomes.get(id);
    }

    void put(CustomBiome biome) {
        biomes.put(biome.id, biome);
    }

    Collection<CustomBiome> all() {
        return biomes.values();
    }

    List<Map<String, Object>> pending() {
        return pending;
    }

    /**
     * Réécrit entièrement datapack/ à partir des biomes chargés.
     */
    void writeDatapack() throws IOException {
        Path pack = packDir();
        Path data = pack.resolve("data");
        if (Files.exists(data)) {
            try (Stream<Path> walk = Files.walk(data)) {
                for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            }
        }
        Files.createDirectories(pack);

        JsonObject meta = new JsonObject();
        meta.addProperty("description", "Biomes SkyHand (SHBiomeColors)");
        meta.add("min_format", format(94, 1));
        meta.add("max_format", format(999, 0));
        JsonObject mcmeta = new JsonObject();
        mcmeta.add("pack", meta);
        Files.writeString(pack.resolve("pack.mcmeta"), GSON.toJson(mcmeta));

        for (CustomBiome biome : biomes.values()) {
            String[] id = biome.id.split(":", 2);
            Path file = data.resolve(id[0]).resolve("worldgen").resolve("biome").resolve(id[1] + ".json");
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(toJson(biome)));
        }
    }

    private static JsonArray format(int major, int minor) {
        JsonArray array = new JsonArray();
        array.add(major);
        array.add(minor);
        return array;
    }

    /**
     * Même base que l'ancien plugin : température 0.7, humidité 0.8, aucune génération ni apparition naturelle.
     */
    private static JsonObject toJson(CustomBiome biome) {
        JsonObject attributes = new JsonObject();
        attributes.addProperty("minecraft:visual/sky_color", CustomBiome.hex(biome.get(ColorType.SKY)));
        attributes.addProperty("minecraft:visual/fog_color", CustomBiome.hex(biome.get(ColorType.FOG)));
        attributes.addProperty("minecraft:visual/water_fog_color", CustomBiome.hex(biome.get(ColorType.WATER_FOG)));

        JsonObject effects = new JsonObject();
        effects.addProperty("water_color", CustomBiome.hex(biome.get(ColorType.WATER)));
        if (biome.get(ColorType.GRASS) != CustomBiome.DEFAULT) {
            effects.addProperty("grass_color", CustomBiome.hex(biome.get(ColorType.GRASS)));
        }
        if (biome.get(ColorType.FOLIAGE) != CustomBiome.DEFAULT) {
            effects.addProperty("foliage_color", CustomBiome.hex(biome.get(ColorType.FOLIAGE)));
        }

        JsonArray features = new JsonArray();
        for (int i = 0; i < 11; i++) {
            features.add(new JsonArray());
        }
        JsonObject spawners = new JsonObject();
        for (String group : SPAWN_GROUPS) {
            spawners.add(group, new JsonArray());
        }

        JsonObject json = new JsonObject();
        json.add("attributes", attributes);
        json.add("carvers", new JsonArray());
        json.addProperty("downfall", 0.8);
        json.add("effects", effects);
        json.add("features", features);
        json.addProperty("has_precipitation", true);
        json.add("spawn_costs", new JsonObject());
        json.add("spawners", spawners);
        json.addProperty("temperature", 0.7);
        return json;
    }
}
