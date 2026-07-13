package dev.sukkit.izanami.api;

import net.minecraft.server.v1_8_R3.BiomeBase;
import org.bukkit.craftbukkit.v1_8_R3.worldgen.BiomeClientRemap;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Registre des biomes custom Izanami.
 * <p>
 * À l'enregistrement : allocation d'un ID libre (plage 40–127, hors vanilla
 * 0–39 et variantes mutées 128+), insertion dans le registre NMS
 * ({@code BiomeBase.biomes} via le constructeur, {@code BiomeBase.o} pour le
 * nom) et déclaration du remap visuel client auprès de Sukkit.
 * Les biomes deviennent immédiatement résolvables dans la config Izanami
 * (whitelist, biome central) par leur nom.
 */
public final class IzanamiBiomeRegistry {

    private static final int MIN_ID = 40;
    private static final int MAX_ID = 127;

    private static final Map<String, CustomBiome> BY_NAME = new LinkedHashMap<>();

    private IzanamiBiomeRegistry() {}

    static synchronized CustomBiome register(CustomBiome.Builder builder) {
        if (BiomeBase.o.containsKey(builder.name)) {
            throw new IllegalArgumentException("Un biome nomme '" + builder.name + "' existe deja.");
        }

        int id = builder.requestedId >= 0 ? claim(builder.requestedId) : allocate();
        CustomBiome biome = new CustomBiome(id, builder); // le ctor BiomeBase inscrit biomes[id]

        BiomeBase.o.put(biome.ah, biome);
        BiomeClientRemap.setMapping(id, biome.getClientBiome().id);
        BY_NAME.put(builder.name.toLowerCase(Locale.ROOT), biome);
        return biome;
    }

    private static int claim(int id) {
        if (id < MIN_ID || id > MAX_ID) {
            throw new IllegalArgumentException("ID de biome custom hors plage [" + MIN_ID + "," + MAX_ID + "] : " + id);
        }
        if (BiomeBase.getBiomes()[id] != null) {
            throw new IllegalArgumentException("ID de biome deja occupe : " + id
                    + " (" + BiomeBase.getBiomes()[id].ah + ")");
        }
        return id;
    }

    private static int allocate() {
        for (int id = MIN_ID; id <= MAX_ID; id++) {
            if (BiomeBase.getBiomes()[id] == null) {
                return id;
            }
        }
        throw new IllegalStateException("Plus d'ID de biome libre dans [" + MIN_ID + "," + MAX_ID + "]");
    }

    public static CustomBiome get(String name) {
        return name == null ? null : BY_NAME.get(name.toLowerCase(Locale.ROOT));
    }

    public static Collection<CustomBiome> getAll() {
        return Collections.unmodifiableCollection(BY_NAME.values());
    }
}
