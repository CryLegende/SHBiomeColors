package org.Cry_Legende.sHBiomeColors;

import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import org.bukkit.entity.Player;

/**
 * Isolé dans sa propre classe : chargé seulement si WorldEdit/FAWE est présent.
 */
final class WorldEditHook {

    private WorldEditHook() {
    }

    /**
     * {minX, minY, minZ, maxX, maxY, maxZ} de la sélection du joueur, ou null si elle est incomplète.
     */
    static int[] selection(Player player) {
        try {
            Region region = WorldEdit.getInstance().getSessionManager().get(BukkitAdapter.adapt(player))
                .getSelection(BukkitAdapter.adapt(player.getWorld()));
            BlockVector3 min = region.getMinimumPoint();
            BlockVector3 max = region.getMaximumPoint();
            return new int[]{min.x(), min.y(), min.z(), max.x(), max.y(), max.z()};
        } catch (IncompleteRegionException e) {
            return null;
        }
    }
}
