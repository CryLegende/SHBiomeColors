package org.Cry_Legende.sHBiomeColors.network;

import com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI;
import com.bgsoftware.superiorskyblock.api.handlers.GridManager;
import com.bgsoftware.superiorskyblock.api.island.Island;
import org.Cry_Legende.sHSkyBridge.api.SkyBridgeAPI;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.LinkedHashSet;
import java.util.Set;

/** Pont vers SHSkyBridge et SuperiorSkyblock2. Cette classe n'est chargée que si SHSkyBridge est installé. */
public final class SkyBridgeHostGuard implements HostGuard {

    private final SkyBridgeAPI bridge;

    private SkyBridgeHostGuard() {
        this.bridge = SkyBridgeAPI.get();
    }

    /** Point d'entrée : le plugin ne résout cette classe qu'à cet appel, donc jamais sans SHSkyBridge. */
    public static HostGuard open() {
        return new SkyBridgeHostGuard();
    }

    /**
     * Cherche l'île au centre et aux quatre coins de la zone : toutes celles trouvées doivent être hébergées ici.
     */
    @Override
    public Result check(World world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        try {
            GridManager grid = SuperiorSkyblockAPI.getGrid();
            if (!grid.isIslandsWorld(world)) {
                return Result.local();
            }
            double y = (minY + maxY) / 2.0;
            double[][] points = {
                {(minX + maxX) / 2.0, (minZ + maxZ) / 2.0},
                {minX, minZ}, {minX, maxZ}, {maxX, minZ}, {maxX, maxZ}
            };
            Set<String> remote = new LinkedHashSet<>();
            boolean found = false;
            boolean here = false;
            for (double[] point : points) {
                Island island = grid.getIslandAt(new Location(world, point[0], y, point[1]));
                if (island == null) {
                    continue;
                }
                found = true;
                if (island.isSpawn() || bridge.isHostedHere(island.getUniqueId())) {
                    here = true;
                } else {
                    remote.add(bridge.hostOf(island.getUniqueId()));
                }
            }
            if (!found) {
                return Result.unknown();
            }
            if (remote.isEmpty()) {
                return Result.local();
            }
            return here || remote.size() > 1 ? Result.mixed() : Result.remote(remote.iterator().next());
        } catch (RuntimeException e) {
            // SuperiorSkyblock2 pas encore prêt : on ne peut rien affirmer
            return Result.unknown();
        }
    }
}
