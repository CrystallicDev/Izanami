package dev.sukkit.izanami.gen;

import dev.sukkit.izanami.api.CaveBiome;
import dev.sukkit.izanami.api.IzanamiCaveBiomeRegistry;
import dev.sukkit.izanami.world.CaveBiomeSettings;

import java.util.ArrayList;
import java.util.List;

/**
 * Zones de biomes de cave : Voronoï 2D en colonnes (cellules jitterées, comme
 * les biomes de surface) sur la bande souterraine [8, base-height - 16].
 * Une entrée "none" dans la palette = pierre naturelle (pas de biome).
 * <p>
 * Immuable et thread-safe.
 */
public final class CaveZoneMap {

    private final long seed;
    private final int cellSize;
    private final CaveBiome[] palette; // entrées null = pierre naturelle
    private final int yMin;
    private final int yMax;

    private CaveZoneMap(long seed, int cellSize, CaveBiome[] palette, int yMin, int yMax) {
        this.seed = seed;
        this.cellSize = cellSize;
        this.palette = palette;
        this.yMin = yMin;
        this.yMax = yMax;
    }

    /** Validation seule (fail-fast à la création du monde, avant toute génération). */
    public static void validate(CaveBiomeSettings settings) {
        for (String name : settings.getAllowed().keySet()) {
            if (!"none".equalsIgnoreCase(name) && IzanamiCaveBiomeRegistry.get(name) == null) {
                throw new IllegalArgumentException("Biome de cave inconnu : '" + name
                        + "'. Valides : none, " + IzanamiCaveBiomeRegistry.validNames());
            }
        }
    }

    /** @return null si la config est vide/désactivée */
    public static CaveZoneMap resolve(CaveBiomeSettings settings, int baseHeight, long seed) {
        if (!settings.isEnabled()) {
            return null;
        }
        validate(settings);
        List<CaveBiome> palette = new ArrayList<>();
        for (java.util.Map.Entry<String, Integer> entry : settings.getAllowed().entrySet()) {
            CaveBiome biome = "none".equalsIgnoreCase(entry.getKey())
                    ? null : IzanamiCaveBiomeRegistry.get(entry.getKey());
            for (int i = 0; i < entry.getValue(); i++) {
                palette.add(biome);
            }
        }
        return new CaveZoneMap(seed, settings.getCellSize(),
                palette.toArray(new CaveBiome[0]), 8, Math.max(24, baseHeight - 16));
    }

    /** Biome de cave de la colonne (les zones sont verticales sur toute la bande). */
    public CaveBiome biomeAtColumn(int x, int z) {
        int cellX = Math.floorDiv(x, this.cellSize);
        int cellZ = Math.floorDiv(z, this.cellSize);
        long bestDistSq = Long.MAX_VALUE;
        long bestCellX = 0;
        long bestCellZ = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                long cx = cellX + dx;
                long cz = cellZ + dz;
                long jitter = mix(cx, cz, 0xCAB10E5L);
                long px = cx * this.cellSize + Math.floorMod(jitter, this.cellSize);
                long pz = cz * this.cellSize + Math.floorMod(jitter >>> 32, this.cellSize);
                long ddx = px - x;
                long ddz = pz - z;
                long distSq = ddx * ddx + ddz * ddz;
                if (distSq < bestDistSq) {
                    bestDistSq = distSq;
                    bestCellX = cx;
                    bestCellZ = cz;
                }
            }
        }
        int index = (int) Math.floorMod(mix(bestCellX, bestCellZ, 0xCAB1B10L), this.palette.length);
        return this.palette[index];
    }

    public CaveBiome biomeAt(int x, int y, int z) {
        if (y < this.yMin || y > this.yMax) {
            return null;
        }
        return biomeAtColumn(x, z);
    }

    public int getYMin() {
        return this.yMin;
    }

    public int getYMax() {
        return this.yMax;
    }

    private long mix(long cx, long cz, long salt) {
        long h = this.seed ^ salt ^ (cx * 0x9E3779B97F4A7C15L) ^ (cz * 0xC2B2AE3D27D4EB4FL);
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        return h ^ (h >>> 31);
    }
}
