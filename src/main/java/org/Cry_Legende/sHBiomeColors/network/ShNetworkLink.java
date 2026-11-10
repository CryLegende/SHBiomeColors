package org.Cry_Legende.sHBiomeColors.network;

import org.Cry_Legende.sHNetwork.api.SHNetworkAPI;

import java.util.function.BiConsumer;

/** Pont vers SHNetwork. Cette classe n'est chargée que si SHNetwork est installé. */
public final class ShNetworkLink implements NetworkLink {

    private static final String CHANNEL = "shbiomecolors";

    private final SHNetworkAPI api;

    private ShNetworkLink() {
        this.api = SHNetworkAPI.get();
    }

    /** Point d'entrée : le plugin ne résout cette classe qu'à cet appel, donc jamais sans SHNetwork. */
    public static NetworkLink open() {
        return new ShNetworkLink();
    }

    @Override
    public void publish(String json) {
        api.publish(CHANNEL, json);
    }

    @Override
    public void subscribe(BiConsumer<String, String> handler) {
        // Le message arrive sur le fil du bus, le traitement (fichiers, datapack) se fait sur le fil principal
        api.subscribe(CHANNEL, message -> api.sync(() -> handler.accept(message.origin(), message.json())), false);
    }
}
