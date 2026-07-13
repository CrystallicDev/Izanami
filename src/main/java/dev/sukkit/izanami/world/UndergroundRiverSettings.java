package dev.sukkit.izanami.world;

import org.bukkit.configuration.ConfigurationSection;

/**
 * Rivières souterraines (section "underground-rivers") — premier biome de
 * cave Izanami : tunnels d'eau navigables (espace d'air au-dessus pour les
 * bateaux), reliés à la surface par des chutes d'eau (bassin + puits).
 *
 * <pre>
 * underground-rivers:
 *   enabled: true
 *   water-level: 40        # y de la surface de l'eau (0 = auto : base-height - 24)
 *   width: 10              # largeur approximative du tunnel (blocs)
 *   scale: 220             # longueur d'onde des meandres
 *   air-gap: 3             # air au-dessus de l'eau (passage bateaux)
 *   water-depth: 2         # profondeur d'eau
 *   waterfall-spacing: 180 # distance approximative entre chutes d'entree
 *   width-variation: 0.6   # variation du rayon le long de la riviere (0 = uniforme)
 *   pool-intensity: 1.0    # frequence/ampleur des elargissements en lacs (0 = aucun)
 *   waterfall-radius: 5    # rayon max des puits de chute (double cone)
 * </pre>
 */
public final class UndergroundRiverSettings {

    private final boolean enabled;
    private final int waterLevel; // 0 = auto
    private final int width;
    private final int scale;
    private final int airGap;
    private final int waterDepth;
    private final int waterfallSpacing;
    private final double widthVariation;
    private final double poolIntensity;
    private final int waterfallRadius;

    public UndergroundRiverSettings(boolean enabled, int waterLevel, int width, int scale,
                                    int airGap, int waterDepth, int waterfallSpacing,
                                    double widthVariation, double poolIntensity, int waterfallRadius) {
        this.enabled = enabled;
        this.waterLevel = waterLevel <= 0 ? 0 : Math.max(16, Math.min(100, waterLevel));
        this.width = Math.max(4, Math.min(24, width));
        this.scale = Math.max(80, Math.min(2000, scale));
        this.airGap = Math.max(2, Math.min(6, airGap));
        this.waterDepth = Math.max(1, Math.min(4, waterDepth));
        this.waterfallSpacing = Math.max(48, Math.min(2000, waterfallSpacing));
        this.widthVariation = Math.max(0.0, Math.min(2.0, widthVariation));
        this.poolIntensity = Math.max(0.0, Math.min(3.0, poolIntensity));
        this.waterfallRadius = Math.max(2, Math.min(10, waterfallRadius));
    }

    public static UndergroundRiverSettings disabled() {
        return new UndergroundRiverSettings(false, 0, 10, 220, 3, 2, 180, 0.6, 1.0, 5);
    }

    public static UndergroundRiverSettings fromSection(ConfigurationSection section) {
        if (section == null) {
            return disabled();
        }
        return new UndergroundRiverSettings(
                section.getBoolean("enabled", true),
                section.getInt("water-level", 0),
                section.getInt("width", 10),
                section.getInt("scale", 220),
                section.getInt("air-gap", 3),
                section.getInt("water-depth", 2),
                section.getInt("waterfall-spacing", 180),
                section.getDouble("width-variation", 0.6),
                section.getDouble("pool-intensity", 1.0),
                section.getInt("waterfall-radius", 5));
    }

    public void toSection(ConfigurationSection section) {
        section.set("enabled", this.enabled);
        section.set("water-level", this.waterLevel);
        section.set("width", this.width);
        section.set("scale", this.scale);
        section.set("air-gap", this.airGap);
        section.set("water-depth", this.waterDepth);
        section.set("waterfall-spacing", this.waterfallSpacing);
        section.set("width-variation", this.widthVariation);
        section.set("pool-intensity", this.poolIntensity);
        section.set("waterfall-radius", this.waterfallRadius);
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    /** Niveau d'eau effectif : configuré, sinon base-height - 24. */
    public int resolveWaterLevel(int baseHeight) {
        return this.waterLevel > 0 ? this.waterLevel : Math.max(20, baseHeight - 24);
    }

    public int getWidth() {
        return this.width;
    }

    public int getScale() {
        return this.scale;
    }

    public int getAirGap() {
        return this.airGap;
    }

    public int getWaterDepth() {
        return this.waterDepth;
    }

    public int getWaterfallSpacing() {
        return this.waterfallSpacing;
    }

    public double getWidthVariation() {
        return this.widthVariation;
    }

    public double getPoolIntensity() {
        return this.poolIntensity;
    }

    public int getWaterfallRadius() {
        return this.waterfallRadius;
    }
}
