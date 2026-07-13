package dev.sukkit.izanami.api;

/**
 * Source de biomes d'un monde : couche surface (2D, chunk + client) et couche
 * cave (3D, serveur uniquement), séparées comme en 1.18+. Récupérable via
 * {@code WorldService#getBiomeSource(String)} — utile aux plugins tiers (ex.
 * gameplay dépendant du biome de cave sous le joueur).
 */
public final class IzanamiBiomeSource {

    private final SurfaceBiomeMap surface;
    private final CaveBiomeMap cave; // null = pas de biomes de cave

    public IzanamiBiomeSource(SurfaceBiomeMap surface, CaveBiomeMap cave) {
        if (surface == null) {
            throw new IllegalArgumentException("surface null");
        }
        this.surface = surface;
        this.cave = cave;
    }

    public SurfaceBiomeMap getSurface() {
        return this.surface;
    }

    /** @return la couche cave, ou null si ce monde n'en définit pas */
    public CaveBiomeMap getCave() {
        return this.cave;
    }

    /** Biome de cave à cette position, ou null (cave standard / pas de couche cave). */
    public CaveBiome caveBiomeAt(int x, int y, int z) {
        return this.cave == null ? null : this.cave.caveBiomeAt(x, y, z);
    }
}
