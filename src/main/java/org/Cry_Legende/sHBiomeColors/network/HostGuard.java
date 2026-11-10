package org.Cry_Legende.sHBiomeColors.network;

import org.bukkit.World;

/**
 * Dit quel serveur héberge l'île sous une zone. Cette interface ne mentionne aucune classe de SHSkyBridge ni de
 * SuperiorSkyblock2 : sans eux, le plugin ne charge jamais {@link SkyBridgeHostGuard} et ne vérifie rien.
 */
public interface HostGuard {

    enum Kind {
        /** Hébergée ici, ou dans un monde qui n'est pas un monde d'îles (spawn, lobby...). */
        LOCAL,
        /** Hébergée par un autre serveur. */
        REMOTE,
        /** À cheval sur plusieurs hôtes. */
        MIXED,
        /** Aucune île trouvée (îles pas encore chargées, ou zone hors de toute île). */
        UNKNOWN
    }

    /** @param host serveur hôte, renseigné seulement pour {@link Kind#REMOTE} */
    record Result(Kind kind, String host) {

        public static Result local() {
            return new Result(Kind.LOCAL, null);
        }

        public static Result remote(String host) {
            return new Result(Kind.REMOTE, host);
        }

        public static Result mixed() {
            return new Result(Kind.MIXED, null);
        }

        public static Result unknown() {
            return new Result(Kind.UNKNOWN, null);
        }
    }

    Result check(World world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ);
}
