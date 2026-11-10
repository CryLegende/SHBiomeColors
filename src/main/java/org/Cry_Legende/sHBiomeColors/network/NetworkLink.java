package org.Cry_Legende.sHBiomeColors.network;

import java.util.function.BiConsumer;

/**
 * Ce dont le mode réseau a besoin de SHNetwork. Cette interface ne mentionne aucune classe de SHNetwork :
 * le plugin la manipule sans jamais charger {@link ShNetworkLink}, donc sans SHNetwork il ne se passe rien.
 */
public interface NetworkLink {

    /** Publie le message (JSON) sur le canal du plugin, à destination des autres serveurs. */
    void publish(String json);

    /**
     * Écoute le canal du plugin. Le gestionnaire reçoit le nom du serveur d'origine et le message (JSON),
     * toujours sur le fil principal.
     */
    void subscribe(BiConsumer<String, String> handler);
}
