package dev.sukkit.izanami.gen;

import net.minecraft.server.v1_8_R3.BiomeBase;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Résolution des noms de biomes de la config vers les BiomeBase NMS.
 * Tolérant sur la forme : "Extreme Hills", "extreme_hills", "EXTREME-HILLS"
 * et les IDs numériques sont tous acceptés.
 */
public final class BiomeResolver {

    private BiomeResolver() {}

    /**
     * @return le biome correspondant, ou null si inconnu
     */
    public static BiomeBase resolve(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        try {
            int id = Integer.parseInt(name.trim());
            return id >= 0 && id < 256 ? BiomeBase.getBiome(id) : null;
        } catch (NumberFormatException ignored) {
            // nom textuel
        }
        return index().get(normalize(name));
    }

    /** Liste lisible des noms valides, pour les messages d'erreur. */
    public static String validNames() {
        StringJoiner joiner = new StringJoiner(", ");
        for (BiomeBase biome : BiomeBase.getBiomes()) {
            if (biome != null && biome.ah != null) {
                joiner.add(biome.ah);
            }
        }
        return joiner.toString();
    }

    /**
     * Index reconstruit à chaque appel (n'a lieu qu'à la création d'un monde) :
     * les biomes custom enregistrés après coup sont ainsi toujours visibles.
     */
    private static Map<String, BiomeBase> index() {
        Map<String, BiomeBase> map = new LinkedHashMap<>();
        for (BiomeBase biome : BiomeBase.getBiomes()) {
            if (biome != null && biome.ah != null) {
                map.put(normalize(biome.ah), biome);
            }
        }
        return map;
    }

    private static String normalize(String name) {
        return name.toLowerCase(Locale.ROOT).replace(" ", "").replace("_", "").replace("-", "");
    }
}
