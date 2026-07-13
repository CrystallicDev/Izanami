package dev.sukkit.izanami.gen;

import dev.sukkit.izanami.world.BiomeSettings;
import net.minecraft.server.v1_8_R3.BiomeBase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@link BiomeSettings} validés et résolus en BiomeBase NMS, prêts pour le
 * {@link IzanamiWorldChunkManager}. La résolution échoue en bloc avec un
 * message clair si un nom de biome est inconnu — à faire AVANT la création
 * du monde (fail-fast), jamais pendant.
 */
public final class ResolvedBiomeSettings {

    private final int cellSize;
    private final BiomeBase[] palette;      // étendue par poids, tirage uniforme dessus
    private final BiomeBase[] altPalette;   // palette sans le biome central (anneau d'exclusion)
    private final List<BiomeBase> distinct; // biomes uniques (palette + centre), pour le spawn
    private final BiomeBase centerBiome;    // nullable
    private final int centerRadius;
    private final int centerEdgeNoise;
    private final int exclusionRadius;
    private final int borderNoise;

    private ResolvedBiomeSettings(int cellSize, BiomeBase[] palette, BiomeBase[] altPalette,
                                  List<BiomeBase> distinct, BiomeBase centerBiome,
                                  int centerRadius, int centerEdgeNoise, int exclusionRadius, int borderNoise) {
        this.cellSize = cellSize;
        this.palette = palette;
        this.altPalette = altPalette;
        this.distinct = distinct;
        this.centerBiome = centerBiome;
        this.centerRadius = centerRadius;
        this.centerEdgeNoise = centerEdgeNoise;
        this.exclusionRadius = exclusionRadius;
        this.borderNoise = borderNoise;
    }

    /**
     * @throws IllegalArgumentException si un biome est inconnu ou si la config est vide
     */
    public static ResolvedBiomeSettings resolve(BiomeSettings settings) {
        List<BiomeBase> palette = new ArrayList<>();
        Set<BiomeBase> distinct = new LinkedHashSet<>();

        for (Map.Entry<String, Integer> entry : settings.getAllowed().entrySet()) {
            BiomeBase biome = resolveOrThrow(entry.getKey());
            distinct.add(biome);
            for (int i = 0; i < entry.getValue(); i++) {
                palette.add(biome);
            }
        }

        BiomeBase centerBiome = null;
        if (settings.getCenterBiome() != null) {
            centerBiome = resolveOrThrow(settings.getCenterBiome());
            distinct.add(centerBiome);
        }

        if (palette.isEmpty()) {
            if (centerBiome == null) {
                throw new IllegalArgumentException(
                        "Config biomes vide : renseigner 'allowed' et/ou 'center.biome'.");
            }
            palette.add(centerBiome); // uniquement un biome central -> monde mono-biome
        }

        List<BiomeBase> alt = new ArrayList<>();
        for (BiomeBase biome : palette) {
            if (biome != centerBiome) {
                alt.add(biome);
            }
        }
        boolean ringActive = centerBiome != null && settings.getExclusionRadius() > settings.getCenterRadius();
        if (ringActive && alt.isEmpty()) {
            throw new IllegalArgumentException("center.exclusion-radius exige au moins un biome "
                    + "'allowed' différent du biome central (l'anneau doit être rempli par autre chose).");
        }

        return new ResolvedBiomeSettings(
                settings.getCellSize(),
                palette.toArray(new BiomeBase[0]),
                alt.toArray(new BiomeBase[0]),
                Collections.unmodifiableList(new ArrayList<>(distinct)),
                centerBiome,
                settings.getCenterRadius(),
                settings.getCenterEdgeNoise(),
                settings.getExclusionRadius(),
                settings.getBorderNoise()
        );
    }

    private static BiomeBase resolveOrThrow(String name) {
        BiomeBase biome = BiomeResolver.resolve(name);
        if (biome == null) {
            throw new IllegalArgumentException("Biome inconnu : '" + name
                    + "'. Biomes valides : " + BiomeResolver.validNames());
        }
        return biome;
    }

    public int getCellSize() {
        return this.cellSize;
    }

    public BiomeBase[] getPalette() {
        return this.palette;
    }

    public List<BiomeBase> getDistinctBiomes() {
        return this.distinct;
    }

    public BiomeBase getCenterBiome() {
        return this.centerBiome;
    }

    public int getCenterRadius() {
        return this.centerRadius;
    }

    public int getCenterEdgeNoise() {
        return this.centerEdgeNoise;
    }

    public BiomeBase[] getAltPalette() {
        return this.altPalette;
    }

    public int getExclusionRadius() {
        return this.exclusionRadius;
    }

    public int getBorderNoise() {
        return this.borderNoise;
    }
}
