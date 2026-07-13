package dev.sukkit.izanami.gen;

import dev.sukkit.izanami.api.CaveBiome;
import dev.sukkit.izanami.api.CaveBiomeMap;
import dev.sukkit.izanami.api.IzanamiCaveBiomes;
import dev.sukkit.izanami.world.UndergroundRiverSettings;

/**
 * Couche de biomes de cave d'un monde Izanami : la rivière souterraine
 * (prioritaire, même formule de bruit que le carver) puis les zones Voronoï
 * ({@link CaveZoneMap}).
 * <p>
 * Immuable et thread-safe.
 */
public final class IzanamiCaveBiomeMap implements CaveBiomeMap {

    private final long seed;
    private final UndergroundRiverSettings rivers; // peut être disabled
    private final int riverWaterLevel;
    private final double riverThreshold;
    private final CaveZoneMap zones; // nullable

    public IzanamiCaveBiomeMap(long seed, UndergroundRiverSettings rivers, CaveZoneMap zones, int baseHeight) {
        this.seed = seed;
        this.rivers = rivers;
        this.zones = zones;
        this.riverWaterLevel = rivers.resolveWaterLevel(baseHeight);
        this.riverThreshold = rivers.getWidth() / (rivers.getScale() * 3.25);
    }

    /** @return les zones Voronoï, ou null (utilisé par le populator de lacs) */
    public CaveZoneMap getZones() {
        return this.zones;
    }

    @Override
    public CaveBiome caveBiomeAt(int x, int y, int z) {
        if (this.rivers.isEnabled()
                && y >= this.riverWaterLevel - this.rivers.getWaterDepth() - 2
                && y <= this.riverWaterLevel + this.rivers.getAirGap() + 2) {
            double scale = this.rivers.getScale();
            if (Math.abs(IzanamiCarver.ridgeNoise(this.seed, x / scale, z / scale)) < this.riverThreshold) {
                return IzanamiCaveBiomes.UNDERGROUND_RIVER;
            }
        }
        return this.zones == null ? null : this.zones.biomeAt(x, y, z);
    }
}
