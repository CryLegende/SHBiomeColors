package org.Cry_Legende.sHBiomeColors;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Biome personnalisé : seules les couleurs changent, le reste reprend les réglages de l'ancien CustomBiomeColors.
 */
final class CustomBiome {

    /**
     * Herbe et feuillage uniquement : laisse le jeu calculer la couleur.
     */
    static final int DEFAULT = -1;

    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    enum ColorType {
        GRASS("herbe", "setgrasscolor", "grass", true),
        FOLIAGE("feuillage", "setfoliagecolor", "foliage", true),
        WATER("eau", "setwatercolor", "water", false),
        WATER_FOG("brouillard-eau", "setwaterfogcolor", "waterfog", false),
        SKY("ciel", "setskycolor", "sky", false),
        FOG("brouillard", "setfogcolor", "fog", false);

        final String label;
        final String command;
        final String permission;
        final boolean allowDefault;

        ColorType(String label, String command, String permission, boolean allowDefault) {
            this.label = label;
            this.command = command;
            this.permission = "shbiomecolors." + permission;
            this.allowDefault = allowDefault;
        }
    }

    final String id;
    // Valeurs par défaut : celles d'un biome tempéré vanilla
    private final int[] colors = {DEFAULT, DEFAULT, 0x3F76E4, 0x050533, 0x78A7FF, 0xC0D8FF};

    CustomBiome(String id) {
        this.id = id;
    }

    int get(ColorType type) {
        return colors[type.ordinal()];
    }

    void set(ColorType type, int color) {
        colors[type.ordinal()] = color;
    }

    CustomBiome copy(String newId) {
        CustomBiome biome = new CustomBiome(newId);
        System.arraycopy(colors, 0, biome.colors, 0, colors.length);
        return biome;
    }

    static boolean isValidId(String id) {
        return ID.matcher(id).matches();
    }

    static String hex(int color) {
        return color == DEFAULT ? "defaut" : String.format("#%06x", color & 0xFFFFFF);
    }

    /**
     * Accepte "#3f76e4", "3F76E4" ou "defaut" ; lève IllegalArgumentException sinon.
     */
    static int parse(String raw) {
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.equals("defaut") || value.equals("default")) {
            return DEFAULT;
        }
        if (value.startsWith("#")) {
            value = value.substring(1);
        }
        if (!value.matches("[0-9a-f]{6}")) {
            throw new IllegalArgumentException(raw);
        }
        return Integer.parseInt(value, 16);
    }

    Map<String, Object> toYaml() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        for (ColorType type : ColorType.values()) {
            map.put(type.label, hex(get(type)));
        }
        return map;
    }

    static CustomBiome fromYaml(Map<?, ?> map) {
        Object id = map.get("id");
        if (id == null || !isValidId(id.toString())) {
            return null;
        }
        CustomBiome biome = new CustomBiome(id.toString());
        for (ColorType type : ColorType.values()) {
            Object raw = map.get(type.label);
            if (raw != null) {
                int color = parse(raw.toString());
                if (color != DEFAULT || type.allowDefault) {
                    biome.set(type, color);
                }
            }
        }
        return biome;
    }

    /**
     * Ancien format CustomBiomeColors : [herbe, feuillage, eau, brouillard-eau, ciel, brouillard], 0 = défaut pour herbe/feuillage.
     */
    static CustomBiome fromLegacy(String id, int[] values) {
        CustomBiome biome = new CustomBiome(id);
        ColorType[] types = ColorType.values();
        for (int i = 0; i < types.length && i < values.length; i++) {
            biome.set(types[i], values[i] == 0 && types[i].allowDefault ? DEFAULT : values[i]);
        }
        return biome;
    }
}
