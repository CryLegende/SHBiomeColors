package org.Cry_Legende.sHBiomeColors;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

/**
 * Textes de messages.yml. S'il n'existe pas encore, il est créé à partir de l'ancien préfixe et de l'ancienne section
 * « messages » de config.yml (textes déjà personnalisés gardés), complétés par ceux du jar. Les clés absentes du
 * fichier prennent la valeur du jar.
 */
final class Messages {

    private final JavaPlugin plugin;
    private volatile YamlConfiguration yaml = new YamlConfiguration();

    Messages(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    private YamlConfiguration jar() {
        YamlConfiguration jar = new YamlConfiguration();
        try (InputStream in = plugin.getResource("messages.yml")) {
            if (in != null) {
                jar.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().log(Level.SEVERE, "messages.yml du jar illisible : " + e.getMessage());
        }
        return jar;
    }

    /** Lit messages.yml en mode strict et remplace les textes en mémoire d'un bloc. */
    void load() throws IOException, InvalidConfigurationException {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            YamlConfiguration created = jar();
            File configFile = new File(plugin.getDataFolder(), "config.yml");
            if (configFile.exists()) {
                YamlConfiguration legacy = YamlConfiguration.loadConfiguration(configFile);
                boolean migrated = false;
                if (legacy.isString("prefix")) {
                    created.set("prefix", legacy.getString("prefix"));
                    migrated = true;
                }
                var section = legacy.getConfigurationSection("messages");
                if (section != null) {
                    for (String key : section.getKeys(false)) {
                        if (section.isString(key)) {
                            created.set(key, section.getString(key));
                            migrated = true;
                        }
                    }
                }
                if (migrated) {
                    plugin.getLogger().info("Messages repris de config.yml dans messages.yml.");
                }
            }
            plugin.getDataFolder().mkdirs();
            created.save(file);
        }
        YamlConfiguration loaded = new YamlConfiguration();
        loaded.load(file);
        loaded.setDefaults(jar());
        yaml = loaded;
    }

    /** Textes du jar seulement, quand messages.yml est illisible au démarrage. */
    void useJar() {
        yaml = jar();
    }

    /** Texte brut (codes &) ; la clé elle-même si elle est inconnue. */
    String get(String key) {
        String text = yaml.getString(key);
        return text != null ? text : key;
    }
}
