package org.Cry_Legende.sHBiomeColors;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;

import java.io.IOException;
import java.nio.file.Files;

/**
 * Exécuté avant le chargement des datapacks et des mondes : sans ça, les chunks utilisant
 * un biome personnalisé seraient chargés en "plains" et réenregistrés ainsi (perte définitive).
 */
public class SHBiomeColorsBootstrap implements PluginBootstrap {

    @Override
    public void bootstrap(BootstrapContext context) {
        BiomeStore store = new BiomeStore(context.getDataDirectory());
        try {
            int imported = store.load();
            if (imported > 0) {
                context.getLogger().info(imported + " biomes importés depuis CustomBiomeColors/data.json.");
            }
            store.writeDatapack();
            store.warnings.forEach(context.getLogger()::warn);
            context.getLogger().info(store.all().size() + " biomes personnalisés ajoutés au datapack.");
        } catch (IOException | RuntimeException e) {
            context.getLogger().error("Datapack SHBiomeColors impossible à générer : " + e);
            // Le datapack déjà sur disque reste chargé : sans lui, les chunks à biome personnalisé repasseraient en plains
            if (!Files.exists(store.packDir().resolve("pack.mcmeta"))) {
                return;
            }
        }

        context.getLifecycleManager().registerEventHandler(LifecycleEvents.DATAPACK_DISCOVERY, event -> {
            try {
                event.registrar().discoverPack(store.packDir(), "biomes", c -> c.autoEnableOnServerStart(true));
            } catch (IOException e) {
                context.getLogger().error("Datapack SHBiomeColors non chargé : " + e.getMessage());
            }
        });
    }
}
