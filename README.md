<div align="center">

<img src="assets/logo.png" alt="SkyHand" width="140">

# SHBiomeColors

*Couleurs de biome personnalisées (herbe, feuillage, eau, ciel, brouillard) via un datapack généré au démarrage*

<img src="https://img.shields.io/badge/Paper-26.3-f2a900?style=for-the-badge" alt="Paper 26.3"> <img src="https://img.shields.io/badge/Java-25-e76f00?style=for-the-badge" alt="Java 25"> <img src="https://img.shields.io/badge/SkyHand-Opale-3aa655?style=for-the-badge" alt="SkyHand Opale">

</div>

---

## Présentation

Couleurs de biome personnalisées (herbe, feuillage, eau, ciel, brouillard) via un datapack généré au démarrage

Utilisé sur : **Opale**.

## Dépendances

- Optionnelles : `WorldEdit` ou `FastAsyncWorldEdit` (sélection des commandes `/set...color`), `SHNetwork`, `SHSkyBridge` et `SuperiorSkyblock2` (mode réseau)

## Mode réseau

Pour plusieurs serveurs de jeu qui partagent les mêmes îles (chaque île a un seul serveur hôte). La section `reseau` de `config.yml` le règle ; `reseau.actif: false` le coupe entièrement.

**Biomes partagés (avec SHNetwork).** Un biome créé ou modifié par `/set...color` est enregistré sur le serveur (biomes.yml et datapack) puis publié en entier sur le canal `shbiomecolors`. Les autres serveurs l'ajoutent à leur biomes.yml et à leur datapack, sans toucher à aucune zone et sans le republier ; un biome identique à celui qu'ils ont déjà ne change rien. Les nouvelles couleurs sont actives au prochain redémarrage de chaque serveur, comme en local.

Le bus n'est pas durable (un serveur arrêté rate les messages). Pour rattraper, chaque serveur annonce ses biomes une dizaine de secondes après son démarrage : ceux qui tournent lui envoient les biomes qui lui manquent, et il reçoit la même chose en retour. Un biome présent partout mais avec d'autres couleurs n'est jamais écrasé, il est signalé dans les logs : refaire la commande de couleur aligne les serveurs. Ce rattrapage se rejoue à chaque redémarrage.

**Hôte des îles (avec SHSkyBridge et SuperiorSkyblock2).**
- Une commande `/set...color` dont la sélection est dans une île hébergée par un autre serveur, ou à cheval sur plusieurs hôtes, est refusée (messages `ile-autre-serveur` et `selection-plusieurs-serveurs`) : rien n'est modifié ni diffusé.
- Une zone mise en attente d'un redémarrage n'est appliquée que si son île est hébergée ici. Sinon, ou si l'île est introuvable pour l'instant, la zone reste dans `en-attente.yml`, intacte ; elle est revue toutes les `reseau.relecture-zones` secondes (60 par défaut, 0 pour ne pas réessayer).
- Un monde qui n'est pas un monde d'îles (spawn, lobby) n'est pas concerné.

Limites : une zone en attente reste sur le serveur où la commande a été lancée, même si l'île change d'hôte avant le redémarrage ; une modification à la main de biomes.yml n'est pas diffusée.

Sans SHNetwork ni SHSkyBridge, ou sans la section `reseau`, le plugin se comporte comme avant : aucun canal, aucune vérification d'hôte.

## Permissions

| Permission | Description | Par défaut |
|---|---|---|
| `shbiomecolors.admin` | Toutes les commandes SHBiomeColors | `op` |
| `shbiomecolors.grass` | — | `op` |
| `shbiomecolors.foliage` | — | `op` |
| `shbiomecolors.water` | — | `op` |
| `shbiomecolors.waterfog` | — | `op` |
| `shbiomecolors.sky` | — | `op` |
| `shbiomecolors.fog` | — | `op` |
| `shbiomecolors.get` | — | `op` |

## Configuration

Fichiers par défaut repris du serveur (secrets remplacés par `CHANGE_ME`). Cliquez sur un fichier pour l'ouvrir :

<pre>
plugins/SHBiomeColors/
└── <a href="src/main/resources/config.yml">config.yml</a>
</pre>

## Compilation

Java 25 et Maven :

```bash
mvn clean package
```

Le jar est généré dans `target/`.

---

<div align="center">

<sub>Développé par <b>Cry_Legende</b> pour le serveur <b>SkyHand</b> · Tous droits réservés</sub>

</div>
