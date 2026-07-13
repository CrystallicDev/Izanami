package dev.sukkit.izanami.world;

import org.bukkit.configuration.ConfigurationSection;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Paramètres de biomes d'un monde Izanami, tels que persistés dans config.yml.
 * Les noms de biomes sont conservés bruts ici ; leur résolution en BiomeBase
 * (avec validation) est faite par {@code ResolvedBiomeSettings}.
 *
 * <pre>
 * biomes:
 *   cell-size: 256          # taille approximative des zones de biomes (blocs)
 *   allowed:                # nom -> poids (ou simple liste, poids 1)
 *     plains: 3
 *     forest: 2
 *   border-noise: 24        # amplitude (blocs) du bruit des bordures, 0 = droites
 *   center:
 *     biome: desert         # biome du centre de la carte
 *     radius: 250           # rayon (circulaire) de la zone centrale
 *     edge-noise: 30        # irrégularité du bord (blocs) : plein jusqu'a radius-edge-noise,
 *                           # jamais au-dela de radius+edge-noise. 0 = cercle net
 *     exclusion-radius: 400 # le biome central est INTERDIT entre le bord et cette limite
 * </pre>
 */
public final class BiomeSettings {

    public static final int DEFAULT_CELL_SIZE = 256;
    public static final int DEFAULT_CENTER_RADIUS = 250;
    public static final int DEFAULT_BORDER_NOISE = 24;
    public static final int DEFAULT_CENTER_EDGE_NOISE = 30;
    private static final int MIN_CELL_SIZE = 16;

    private final int cellSize;
    private final Map<String, Integer> allowed;
    private final String centerBiome;
    private final int centerRadius;
    private final int centerEdgeNoise;
    private final int exclusionRadius;
    private final int borderNoise;

    public BiomeSettings(int cellSize, Map<String, Integer> allowed, String centerBiome,
                         int centerRadius, int centerEdgeNoise, int exclusionRadius, int borderNoise) {
        this.cellSize = Math.max(MIN_CELL_SIZE, cellSize);
        this.allowed = allowed;
        this.centerBiome = centerBiome;
        this.centerRadius = centerRadius;
        this.centerEdgeNoise = Math.max(0, Math.min(centerRadius / 2, centerEdgeNoise));
        this.exclusionRadius = Math.max(centerRadius, exclusionRadius);
        this.borderNoise = Math.max(0, Math.min(64, borderNoise));
    }

    /** @return null si la section est absente (le monde garde les biomes vanilla) */
    public static BiomeSettings fromSection(ConfigurationSection section) {
        if (section == null) {
            return null;
        }

        Map<String, Integer> allowed = new LinkedHashMap<>();
        ConfigurationSection allowedSection = section.getConfigurationSection("allowed");
        if (allowedSection != null) {
            for (String name : allowedSection.getKeys(false)) {
                allowed.put(name, Math.max(1, allowedSection.getInt(name, 1)));
            }
        } else {
            List<String> allowedList = section.getStringList("allowed");
            for (String name : allowedList) {
                allowed.put(name, 1);
            }
        }

        String centerBiome = null;
        int centerRadius = DEFAULT_CENTER_RADIUS;
        int centerEdgeNoise = DEFAULT_CENTER_EDGE_NOISE;
        int exclusionRadius = centerRadius;
        ConfigurationSection center = section.getConfigurationSection("center");
        if (center != null) {
            centerBiome = center.getString("biome");
            centerRadius = center.getInt("radius", DEFAULT_CENTER_RADIUS);
            centerEdgeNoise = center.getInt("edge-noise", DEFAULT_CENTER_EDGE_NOISE);
            exclusionRadius = center.getInt("exclusion-radius", centerRadius);
        }

        return new BiomeSettings(
                section.getInt("cell-size", DEFAULT_CELL_SIZE),
                allowed, centerBiome, centerRadius, centerEdgeNoise, exclusionRadius,
                section.getInt("border-noise", DEFAULT_BORDER_NOISE));
    }

    public void toSection(ConfigurationSection section) {
        section.set("cell-size", this.cellSize);
        section.set("border-noise", this.borderNoise);
        ConfigurationSection allowedSection = section.createSection("allowed");
        for (Map.Entry<String, Integer> entry : this.allowed.entrySet()) {
            allowedSection.set(entry.getKey(), entry.getValue());
        }
        if (this.centerBiome != null) {
            ConfigurationSection center = section.createSection("center");
            center.set("biome", this.centerBiome);
            center.set("radius", this.centerRadius);
            center.set("edge-noise", this.centerEdgeNoise);
            center.set("exclusion-radius", this.exclusionRadius);
        }
    }

    public int getCellSize() {
        return this.cellSize;
    }

    /** nom brut -> poids (>= 1) */
    public Map<String, Integer> getAllowed() {
        return this.allowed;
    }

    public String getCenterBiome() {
        return this.centerBiome;
    }

    public int getCenterRadius() {
        return this.centerRadius;
    }

    /** Irrégularité du bord de la zone centrale (blocs). 0 = cercle net. */
    public int getCenterEdgeNoise() {
        return this.centerEdgeNoise;
    }

    /** Limite au-delà de radius où le biome central est interdit (== radius : pas d'anneau). */
    public int getExclusionRadius() {
        return this.exclusionRadius;
    }

    /** Amplitude (blocs) du bruit appliqué aux bordures de biomes. 0 = bordures droites. */
    public int getBorderNoise() {
        return this.borderNoise;
    }
}
