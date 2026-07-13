package dev.sukkit.izanami.api;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Registre des biomes de cave. Les noms de config sont normalisés
 * ("Deepslate Caves" ↔ "deepslate_caves"). Le nom réservé "none" désigne
 * l'absence de biome (pierre naturelle) dans les zones souterraines.
 */
public final class IzanamiCaveBiomeRegistry {

    private static final Map<String, CaveBiome> BY_NAME = new LinkedHashMap<>();

    static {
        register(IzanamiCaveBiomes.UNDERGROUND_RIVER);
    }

    private IzanamiCaveBiomeRegistry() {}

    public static synchronized void register(CaveBiome biome) {
        String key = normalize(biome.getName());
        if ("none".equals(key)) {
            throw new IllegalArgumentException("'none' est un nom réservé");
        }
        if (BY_NAME.containsKey(key)) {
            throw new IllegalArgumentException("Biome de cave déjà enregistré : " + biome.getName());
        }
        BY_NAME.put(key, biome);
    }

    public static CaveBiome get(String name) {
        return name == null ? null : BY_NAME.get(normalize(name));
    }

    public static Collection<CaveBiome> getAll() {
        return Collections.unmodifiableCollection(BY_NAME.values());
    }

    public static String validNames() {
        StringJoiner joiner = new StringJoiner(", ");
        for (String key : BY_NAME.keySet()) {
            joiner.add(key);
        }
        return joiner.toString();
    }

    private static String normalize(String name) {
        return name.toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
    }
}
