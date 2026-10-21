package org.Cry_Legende.sHBiomeColors;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.Cry_Legende.sHBiomeColors.CustomBiome.ColorType;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public class SHBiomeColors extends JavaPlugin implements Listener {

    private static final String ID_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789_-";
    private final Random random = new Random();
    private BiomeStore store;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        store = new BiomeStore(getDataFolder().toPath());
        try {
            store.load();
        } catch (IOException e) {
            getLogger().severe("Lecture de biomes.yml impossible : " + e.getMessage());
        }
        long missing = store.all().stream().filter(b -> biomes().get(NamespacedKey.fromString(b.id)) == null).count();
        if (missing > 0) {
            getLogger().warning(missing + " biomes personnalisés ne sont pas chargés : redémarrez le serveur.");
        }
        getServer().getPluginManager().registerEvents(this, this);
        registerCommands();
        Bukkit.getScheduler().runTask(this, () -> Bukkit.getWorlds().forEach(this::applyPending));
    }

    private static Registry<Biome> biomes() {
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME);
    }

    private static String keyOf(Biome biome) {
        NamespacedKey key = biomes().getKey(biome);
        return key == null ? "?" : key.toString();
    }

    // ------------------------------------------------------------------ zones

    private record Zone(String world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

        long volume() {
            return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        }

        Map<String, Object> toYaml(String biome) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("monde", world);
            map.put("biome", biome);
            map.put("min", List.of(minX, minY, minZ));
            map.put("max", List.of(maxX, maxY, maxZ));
            return map;
        }

        static Zone fromYaml(Map<String, Object> map) {
            if (!(map.get("min") instanceof List<?> min) || !(map.get("max") instanceof List<?> max) || min.size() != 3 || max.size() != 3) {
                return null;
            }
            return new Zone(String.valueOf(map.get("monde")), num(min, 0), num(min, 1), num(min, 2), num(max, 0), num(max, 1), num(max, 2));
        }

        private static int num(List<?> list, int index) {
            return ((Number) list.get(index)).intValue();
        }
    }

    /**
     * Applique le biome cellule par cellule (4x4x4) puis renvoie les chunks aux joueurs.
     */
    private int apply(World world, Zone zone, Biome biome) {
        int minY = Math.max(zone.minY, world.getMinHeight());
        int maxY = Math.min(zone.maxY, world.getMaxHeight() - 1);
        int cells = 0;
        for (int x = zone.minX >> 2; x <= zone.maxX >> 2; x++) {
            for (int z = zone.minZ >> 2; z <= zone.maxZ >> 2; z++) {
                for (int y = minY >> 2; y <= maxY >> 2; y++) {
                    world.setBiome(x << 2, y << 2, z << 2, biome);
                    cells++;
                }
            }
        }
        for (int cx = zone.minX >> 4; cx <= zone.maxX >> 4; cx++) {
            for (int cz = zone.minZ >> 4; cz <= zone.maxZ >> 4; cz++) {
                world.refreshChunk(cx, cz);
            }
        }
        return cells;
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        applyPending(event.getWorld());
    }

    private void applyPending(World world) {
        boolean changed = false;
        Iterator<Map<String, Object>> it = store.pending().iterator();
        while (it.hasNext()) {
            Map<String, Object> raw = it.next();
            if (!world.getName().equals(String.valueOf(raw.get("monde")))) {
                continue;
            }
            String id = String.valueOf(raw.get("biome"));
            NamespacedKey key = NamespacedKey.fromString(id);
            Biome biome = key == null ? null : biomes().get(key);
            Zone zone = Zone.fromYaml(raw);
            if (biome == null || zone == null) {
                getLogger().warning("Zone en attente non appliquée dans " + world.getName() + " : biome " + id + " non chargé.");
                continue;
            }
            int cells = apply(world, zone, biome);
            getLogger().info("Biome " + id + " appliqué à " + cells + " cellules dans " + world.getName() + ".");
            it.remove();
            changed = true;
        }
        if (changed) {
            savePending();
        }
    }

    private void savePending() {
        try {
            store.savePending();
        } catch (IOException e) {
            getLogger().severe("Sauvegarde de en-attente.yml impossible : " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ commandes

    private void registerCommands() {
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            var commands = event.registrar();
            for (ColorType type : ColorType.values()) {
                // "//setgrasscolor" : nom d'origine de CustomBiomeColors
                commands.register(type.command, "Couleur " + type.label + " de la sélection WorldEdit",
                    List.of("/" + type.command), new SetColorCommand(type));
            }
            commands.register("getbiomecolors", "Couleurs du biome où vous êtes", List.of("/getbiomecolors"), new GetCommand());
            commands.register("shbiomecolors", "Biomes personnalisés SkyHand", List.of(), new AdminCommand());
        });
    }

    private final class SetColorCommand implements BasicCommand {
        private final ColorType type;

        SetColorCommand(ColorType type) {
            this.type = type;
        }

        @Override
        public String permission() {
            return type.permission;
        }

        @Override
        public void execute(CommandSourceStack source, String[] args) {
            if (!(source.getSender() instanceof Player player)) {
                send(source.getSender(), "joueur-seulement");
                return;
            }
            if (args.length < 1) {
                send(player, "usage", "{commande}", type.command);
                return;
            }
            int color;
            try {
                color = CustomBiome.parse(args[0]);
            } catch (IllegalArgumentException e) {
                send(player, "couleur-invalide");
                return;
            }
            if (color == CustomBiome.DEFAULT && !type.allowDefault) {
                send(player, "defaut-interdit");
                return;
            }
            String id = args.length >= 2 ? normalizeId(args[1]) : randomId();
            if (id == null) {
                send(player, "nom-invalide");
                return;
            }
            Zone zone = selection(player);
            if (zone == null) {
                return;
            }

            CustomBiome biome = store.get(id);
            if (biome == null) {
                // Comme l'ancien plugin : on part des couleurs du biome actuel de la zone
                CustomBiome base = store.get(keyOf(player.getWorld().getBiome(zone.minX, zone.minY, zone.minZ)));
                biome = base != null ? base.copy(id) : new CustomBiome(id);
            }
            biome.set(type, color);
            store.put(biome);
            try {
                store.save();
                store.writeDatapack();
            } catch (IOException e) {
                getLogger().severe("Sauvegarde des biomes impossible : " + e.getMessage());
            }
            send(player, "modifie", "{biome}", id, "{couleur}", type.label, "{valeur}", CustomBiome.hex(color));

            Biome loaded = biomes().get(NamespacedKey.fromString(id));
            if (loaded != null) {
                send(player, "applique", "{biome}", id, "{cellules}", String.valueOf(apply(player.getWorld(), zone, loaded)));
            } else {
                store.pending().add(zone.toYaml(id));
                savePending();
                send(player, "en-attente");
            }
        }

        @Override
        public Collection<String> suggest(CommandSourceStack source, String[] args) {
            if (args.length <= 1) {
                return type.allowDefault ? List.of("#", "defaut") : List.of("#");
            }
            if (args.length == 2) {
                return store.all().stream().map(b -> b.id).filter(i -> i.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
            }
            return List.of();
        }
    }

    private final class GetCommand implements BasicCommand {
        @Override
        public String permission() {
            return "shbiomecolors.get";
        }

        @Override
        public void execute(CommandSourceStack source, String[] args) {
            if (!(source.getSender() instanceof Player player)) {
                send(source.getSender(), "joueur-seulement");
                return;
            }
            String id = keyOf(player.getLocation().getBlock().getBiome());
            send(player, "biome-actuel", "{biome}", id);
            CustomBiome custom = store.get(id);
            if (custom == null) {
                send(player, "biome-vanilla");
                return;
            }
            for (ColorType type : ColorType.values()) {
                send(player, "ligne-couleur", "{couleur}", type.label, "{valeur}", CustomBiome.hex(custom.get(type)));
            }
        }
    }

    private final class AdminCommand implements BasicCommand {
        @Override
        public String permission() {
            return "shbiomecolors.admin";
        }

        @Override
        public void execute(CommandSourceStack source, String[] args) {
            CommandSender sender = source.getSender();
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                reloadConfig();
                try {
                    store.load();
                    store.writeDatapack();
                } catch (IOException e) {
                    getLogger().severe("Rechargement impossible : " + e.getMessage());
                }
                store.warnings.forEach(getLogger()::warning);
                send(sender, "recharge", "{nombre}", String.valueOf(store.all().size()));
                return;
            }
            List<String> ids = new ArrayList<>();
            store.all().forEach(b -> ids.add(b.id));
            send(sender, "liste", "{nombre}", String.valueOf(ids.size()), "{biomes}", String.join("&7, &e", ids));
        }

        @Override
        public Collection<String> suggest(CommandSourceStack source, String[] args) {
            return args.length <= 1 ? List.of("liste", "reload") : List.of();
        }
    }

    // ------------------------------------------------------------------ utilitaires

    private Zone selection(Player player) {
        if (!Bukkit.getPluginManager().isPluginEnabled("FastAsyncWorldEdit") && !Bukkit.getPluginManager().isPluginEnabled("WorldEdit")) {
            send(player, "worldedit-absent");
            return null;
        }
        int[] box = WorldEditHook.selection(player);
        if (box == null) {
            send(player, "selection-requise");
            return null;
        }
        Zone zone = new Zone(player.getWorld().getName(), box[0], box[1], box[2], box[3], box[4], box[5]);
        long max = getConfig().getLong("volume-max", 8_000_000L);
        if (zone.volume() > max) {
            send(player, "selection-trop-grande", "{volume}", String.valueOf(zone.volume()), "{max}", String.valueOf(max));
            return null;
        }
        return zone;
    }

    /**
     * "rouge" devient "cbc:rouge" ; null si le nom est invalide.
     */
    private static String normalizeId(String raw) {
        String id = raw.toLowerCase(Locale.ROOT);
        if (!id.contains(":")) {
            id = "cbc:" + id;
        }
        return CustomBiome.isValidId(id) ? id : null;
    }

    /**
     * Même forme que les identifiants générés par CustomBiomeColors (cbc: + 8 caractères).
     */
    private String randomId() {
        String id;
        do {
            StringBuilder sb = new StringBuilder("cbc:");
            for (int i = 0; i < 8; i++) {
                sb.append(ID_CHARS.charAt(random.nextInt(ID_CHARS.length())));
            }
            id = sb.toString();
        } while (store.get(id) != null);
        return id;
    }

    private void send(CommandSender sender, String key, String... replacements) {
        String message = getConfig().getString("messages." + key, key);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            message = message.replace(replacements[i], replacements[i + 1]);
        }
        sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(getConfig().getString("prefix", "") + message));
    }
}
