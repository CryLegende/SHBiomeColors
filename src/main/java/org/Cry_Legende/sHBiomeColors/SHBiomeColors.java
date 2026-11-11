package org.Cry_Legende.sHBiomeColors;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.Cry_Legende.sHBiomeColors.CustomBiome.ColorType;
import org.Cry_Legende.sHBiomeColors.network.HostGuard;
import org.Cry_Legende.sHBiomeColors.network.ShNetworkLink;
import org.Cry_Legende.sHBiomeColors.network.SkyBridgeHostGuard;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
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
    private Messages messages;
    private BukkitTask zoneRetryTask;
    // Mode réseau : null tant que SHNetwork (partage des biomes) ou SHSkyBridge (hôte des îles) n'est pas là
    private BiomeSync sync;
    private HostGuard hostGuard;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        messages = new Messages(this);
        try {
            messages.load();
        } catch (IOException | InvalidConfigurationException e) {
            getLogger().severe("messages.yml illisible, textes du jar utilisés : " + e.getMessage());
            messages.useJar();
        }
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
        openNetwork();
        Bukkit.getScheduler().runTask(this, () -> Bukkit.getWorlds().forEach(world -> applyPending(world, false)));
    }

    /**
     * Mode réseau : seulement si reseau.actif et si SHNetwork / SHSkyBridge sont chargés. Sans eux rien ne change.
     */
    private void openNetwork() {
        if (!getConfig().getBoolean("reseau.actif", true)) {
            return;
        }
        var plugins = Bukkit.getPluginManager();
        if (plugins.isPluginEnabled("SHNetwork")) {
            try {
                sync = new BiomeSync(getLogger(), store, ShNetworkLink.open());
                sync.start();
                // Laisse le temps au bus de se connecter avant d'annoncer nos biomes
                Bukkit.getScheduler().runTaskLater(this, () -> sync.hello(), 200L);
                getLogger().info("Mode réseau : les biomes sont partagés avec les autres serveurs.");
            } catch (LinkageError | RuntimeException e) {
                sync = null;
                getLogger().warning("SHNetwork présent mais inutilisable, biomes non partagés : " + e);
            }
        }
        if (plugins.isPluginEnabled("SHSkyBridge") && plugins.isPluginEnabled("SuperiorSkyblock2")) {
            try {
                hostGuard = SkyBridgeHostGuard.open();
                getLogger().info("Mode réseau : seules les zones des îles hébergées par ce serveur sont modifiées.");
                scheduleZoneRetry();
            } catch (LinkageError | RuntimeException e) {
                hostGuard = null;
                getLogger().warning("SHSkyBridge présent mais inutilisable, hôte des îles non vérifié : " + e);
            }
        }
    }

    /**
     * Mode réseau avec SHSkyBridge : relance périodique des zones en attente. Appelé à l'activation et au reload,
     * qui annule le timer précédent pour prendre le nouvel intervalle.
     */
    private void scheduleZoneRetry() {
        if (zoneRetryTask != null) {
            zoneRetryTask.cancel();
            zoneRetryTask = null;
        }
        if (hostGuard == null) {
            return;
        }
        int interval = getConfig().getInt("reseau.relecture-zones", 60);
        if (interval > 0) {
            long ticks = interval * 20L;
            zoneRetryTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
                if (!store.pending().isEmpty()) {
                    Bukkit.getWorlds().forEach(world -> applyPending(world, true));
                }
            }, ticks, ticks);
        }
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
        applyPending(event.getWorld(), false);
    }

    /**
     * @param silent vrai pour les passages répétés du mode réseau : les zones gardées ne sont pas signalées à chaque fois
     */
    private void applyPending(World world, boolean silent) {
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
                if (!silent) {
                    getLogger().warning("Zone en attente non appliquée dans " + world.getName() + " : biome " + id + " non chargé.");
                }
                continue;
            }
            if (!hostedHere(world, zone, silent)) {
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

    /**
     * Mode réseau : une zone en attente n'est appliquée que dans une île hébergée ici (ailleurs, ses chunks ne sont
     * pas à jour). Sinon elle reste en attente, intacte, et sera revue au prochain passage.
     */
    private boolean hostedHere(World world, Zone zone, boolean silent) {
        if (hostGuard == null) {
            return true;
        }
        HostGuard.Result result = hostGuard.check(world, zone.minX, zone.minY, zone.minZ, zone.maxX, zone.maxY, zone.maxZ);
        if (result.kind() == HostGuard.Kind.LOCAL) {
            return true;
        }
        if (!silent) {
            String reason = switch (result.kind()) {
                case REMOTE -> "île hébergée par " + result.host();
                case MIXED -> "zone à cheval sur plusieurs serveurs";
                default -> "île introuvable pour l'instant";
            };
            getLogger().info("Zone en attente gardée dans " + world.getName() + " : " + reason + ".");
        }
        return false;
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
            if (zone == null || !hostAllowed(player, zone)) {
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
            boolean saved = false;
            try {
                store.save();
                store.writeDatapack();
                saved = true;
            } catch (IOException e) {
                getLogger().severe("Sauvegarde des biomes impossible : " + e.getMessage());
            }
            if (saved && sync != null) {
                sync.publish(biome);
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

    /** Sous-commandes de /shbiomecolors, dans l'ordre d'affichage. */
    private static final List<String> ADMIN_SUBCOMMANDS = List.of("list", "reload", "info", "pending");

    /** Sous-commandes que l'expéditeur a le droit d'utiliser. */
    private List<String> usableSubcommands(CommandSender sender) {
        List<String> usable = new ArrayList<>();
        for (String sub : ADMIN_SUBCOMMANDS) {
            if (sender.hasPermission("shbiomecolors.admin." + sub)) {
                usable.add(sub);
            }
        }
        return usable;
    }

    private final class AdminCommand implements BasicCommand {
        // Visible dès qu'une sous-commande est permise : un délégué qui n'a qu'admin.reload doit y accéder
        @Override
        public boolean canUse(CommandSender sender) {
            return !usableSubcommands(sender).isEmpty();
        }

        @Override
        public void execute(CommandSourceStack source, String[] args) {
            CommandSender sender = source.getSender();
            List<String> usable = usableSubcommands(sender);
            String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
            // « liste » : ancien nom de list
            if (sub.equals("liste")) {
                sub = "list";
            }
            if (!usable.contains(sub)) {
                send(sender, "admin-usage", "{sous-commandes}", String.join("|", usable));
                return;
            }
            switch (sub) {
                case "reload" -> reloadAll(sender);
                case "info" -> info(sender, args);
                case "pending" -> pending(sender);
                default -> {
                    List<String> ids = new ArrayList<>();
                    store.all().forEach(b -> ids.add(b.id));
                    send(sender, "liste", "{nombre}", String.valueOf(ids.size()), "{biomes}", String.join("&7, &e", ids));
                }
            }
        }

        @Override
        public Collection<String> suggest(CommandSourceStack source, String[] args) {
            CommandSender sender = source.getSender();
            if (args.length <= 1) {
                String typed = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
                return usableSubcommands(sender).stream().filter(name -> name.startsWith(typed)).toList();
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("info") && sender.hasPermission("shbiomecolors.admin.info")) {
                String typed = args[1].toLowerCase(Locale.ROOT);
                return store.all().stream().map(b -> b.id).filter(i -> i.startsWith(typed)).toList();
            }
            return List.of();
        }
    }

    /** Fichier invalide et cause (« fichier|cause »), ou null si config.yml et messages.yml se lisent. */
    private String checkFiles() {
        for (String name : new String[] {"config.yml", "messages.yml"}) {
            File file = new File(getDataFolder(), name);
            if (!file.exists()) {
                continue;
            }
            try {
                new YamlConfiguration().load(file);
            } catch (IOException | InvalidConfigurationException e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                String text = String.valueOf(cause.getMessage()).replaceAll("\\s+", " ").trim();
                return name + "|" + (text.length() > 200 ? text.substring(0, 200) + "..." : text);
            }
        }
        return null;
    }

    /**
     * /shbiomecolors reload : config.yml et messages.yml sont d'abord lus en mode strict, puis biomes.yml et
     * en-attente.yml (lus avant de toucher à l'état). Au moindre fichier invalide rien n'est remplacé.
     */
    private void reloadAll(CommandSender sender) {
        String error = checkFiles();
        if (error == null) {
            try {
                store.load();
                store.writeDatapack();
            } catch (IOException e) {
                getLogger().severe("Rechargement impossible : " + e.getMessage());
                error = "biomes.yml|" + e.getMessage();
            }
        }
        if (error != null) {
            String[] parts = error.split("\\|", 2);
            getLogger().warning("Rechargement refusé, configuration précédente gardée (" + parts[0] + " : " + parts[1] + ").");
            send(sender, "reload-erreur", "{fichier}", parts[0], "{erreur}", parts[1]);
            return;
        }
        reloadConfig();
        try {
            messages.load();
        } catch (IOException | InvalidConfigurationException e) {
            getLogger().warning("messages.yml relu avec erreur : " + e.getMessage());
        }
        store.warnings.forEach(getLogger()::warning);
        scheduleZoneRetry();
        send(sender, "recharge", "{nombre}", String.valueOf(store.all().size()));
        send(sender, "recharge-reseau");
    }

    /** /shbiomecolors info <biome> : les six couleurs d'un biome personnalisé. */
    private void info(CommandSender sender, String[] args) {
        if (args.length < 2) {
            send(sender, "admin-usage-info");
            return;
        }
        String id = normalizeId(args[1]);
        CustomBiome biome = id == null ? null : store.get(id);
        if (biome == null) {
            send(sender, "inconnu", "{biome}", args[1]);
            return;
        }
        send(sender, "info-entete", "{biome}", id);
        for (ColorType type : ColorType.values()) {
            send(sender, "ligne-couleur", "{couleur}", type.label, "{valeur}", CustomBiome.hex(biome.get(type)));
        }
        NamespacedKey key = NamespacedKey.fromString(id);
        if (key == null || biomes().get(key) == null) {
            send(sender, "pas-charge", "{biome}", id);
        }
    }

    /** /shbiomecolors pending : zones qui attendent un redémarrage ou leur île. */
    private void pending(CommandSender sender) {
        List<Map<String, Object>> zones = store.pending();
        if (zones.isEmpty()) {
            send(sender, "pending-vide");
            return;
        }
        send(sender, "pending-entete", "{nombre}", String.valueOf(zones.size()));
        for (Map<String, Object> zone : zones) {
            send(sender, "pending-ligne",
                "{monde}", String.valueOf(zone.get("monde")),
                "{biome}", String.valueOf(zone.get("biome")),
                "{min}", coords(zone.get("min")),
                "{max}", coords(zone.get("max")));
        }
    }

    private static String coords(Object raw) {
        if (raw instanceof List<?> list && list.size() == 3) {
            return list.get(0) + " " + list.get(1) + " " + list.get(2);
        }
        return "?";
    }

    // ------------------------------------------------------------------ utilitaires

    /**
     * Mode réseau : refuse une zone dans une île hébergée par un autre serveur, ou à cheval sur plusieurs.
     */
    private boolean hostAllowed(Player player, Zone zone) {
        if (hostGuard == null) {
            return true;
        }
        HostGuard.Result result = hostGuard.check(player.getWorld(), zone.minX, zone.minY, zone.minZ, zone.maxX, zone.maxY, zone.maxZ);
        switch (result.kind()) {
            case REMOTE -> {
                send(player, "ile-autre-serveur", "{serveur}", result.host());
                return false;
            }
            case MIXED -> {
                send(player, "selection-plusieurs-serveurs");
                return false;
            }
            default -> {
                return true;
            }
        }
    }

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
        String message = messages.get(key);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            message = message.replace(replacements[i], replacements[i + 1]);
        }
        sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(messages.get("prefix") + message));
    }
}
