package dev.sukkit.izanami.gen;

import net.minecraft.server.v1_8_R3.BiomeBase;
import net.minecraft.server.v1_8_R3.BlockPosition;
import net.minecraft.server.v1_8_R3.WorldChunkManager;

import java.util.List;
import java.util.Random;

/**
 * Distribution des biomes par cellules de Voronoï jitterées (déterministe par
 * seed), restreinte à une whitelist pondérée, avec biome central optionnel.
 * Court-circuite les GenLayers vanilla (comme {@code WorldChunkManagerHell}) et
 * calcule chaque échantillon directement, sans cache. Immuable et thread-safe.
 */
public final class IzanamiWorldChunkManager extends WorldChunkManager implements dev.sukkit.izanami.api.SurfaceBiomeMap {

    private final long seed;
    private final int cellSize;
    private final BiomeBase[] palette;
    private final BiomeBase[] altPalette;
    private final List<BiomeBase> spawnBiomes;
    private final BiomeBase centerBiome;
    private final int centerRadius;
    private final int centerEdgeNoise;
    private final int exclusionRadius;
    private final int warpAmplitude;
    /**
     * Marge de sécurité de l'anneau d'exclusion : une cellule peut "déborder"
     * jusqu'à ~2 cellules + l'amplitude du warp au-delà de son centre. Toute
     * cellule du biome central dont le centre est sous exclusionRadius + marge
     * est re-tirée, garantissant zéro apparition dans [radius, exclusionRadius].
     */
    private final int exclusionMargin;

    public IzanamiWorldChunkManager(long seed, ResolvedBiomeSettings settings) {
        // super() : constructeur protégé sans GenLayers (comme WorldChunkManagerHell)
        this.seed = seed;
        this.cellSize = settings.getCellSize();
        this.palette = settings.getPalette();
        this.altPalette = settings.getAltPalette();
        this.spawnBiomes = settings.getDistinctBiomes();
        this.centerBiome = settings.getCenterBiome();
        this.centerRadius = settings.getCenterRadius();
        this.centerEdgeNoise = settings.getCenterEdgeNoise();
        this.exclusionRadius = settings.getExclusionRadius();
        this.warpAmplitude = settings.getBorderNoise();
        this.exclusionMargin = 2 * this.cellSize + this.warpAmplitude;
    }

    @Override
    public BiomeBase surfaceBiomeAt(int x, int z) {
        return biomeAt(x, z);
    }

    /**
     * Cœur de la résolution : biome à la position bloc (x, z).
     */
    public BiomeBase biomeAt(int x, int z) {
        // Zone centrale : disque au bord irrégulier (rayon modulé par bruit).
        // Garanties : plein jusqu'à radius-edgeNoise, jamais au-delà de radius+edgeNoise.
        if (this.centerBiome != null && isInCenterBlob(x, z)) {
            return this.centerBiome;
        }

        // Domain warping : tord les coordonnées avant le lookup Voronoï pour des
        // bordures organiques au lieu d'arêtes droites.
        int wx = x;
        int wz = z;
        if (this.warpAmplitude > 0) {
            wx += warpOffset(x, z, 0x57A6F00DL);
            wz += warpOffset(x, z, 0xC0FFEE42L);
        }

        // Voronoï : centre jitteré par cellule, plus proche parmi le voisinage 3x3.
        int cellX = Math.floorDiv(wx, this.cellSize);
        int cellZ = Math.floorDiv(wz, this.cellSize);
        long bestDistSq = Long.MAX_VALUE;
        long bestCellX = 0;
        long bestCellZ = 0;
        long bestPx = 0;
        long bestPz = 0;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                long cx = cellX + dx;
                long cz = cellZ + dz;
                long jitter = mix(cx, cz, 0x5EED1E55L);
                long px = cx * this.cellSize + Math.floorMod(jitter, this.cellSize);
                long pz = cz * this.cellSize + Math.floorMod(jitter >>> 32, this.cellSize);
                long ddx = px - wx;
                long ddz = pz - wz;
                long distSq = ddx * ddx + ddz * ddz;
                if (distSq < bestDistSq) {
                    bestDistSq = distSq;
                    bestCellX = cx;
                    bestCellZ = cz;
                    bestPx = px;
                    bestPz = pz;
                }
            }
        }

        int index = (int) Math.floorMod(mix(bestCellX, bestCellZ, 0xB10B5EEDL), this.palette.length);
        BiomeBase picked = this.palette[index];

        // Anneau d'exclusion (euclidien) : décision PAR CELLULE (pas par bloc)
        // pour éviter toute couture à la frontière de l'anneau.
        if (picked == this.centerBiome && this.centerBiome != null
                && this.exclusionRadius > this.centerRadius
                && this.altPalette.length > 0) {
            long limit = (long) this.exclusionRadius + this.exclusionMargin;
            if (bestPx * bestPx + bestPz * bestPz <= limit * limit) {
                int altIndex = (int) Math.floorMod(mix(bestCellX, bestCellZ, 0xA17E4A7EL), this.altPalette.length);
                picked = this.altPalette[altIndex];
            }
        }
        return picked;
    }

    /**
     * Appartenance au disque central : distance euclidienne comparée au rayon
     * modulé par un bruit de valeur 2 octaves (grilles 96 et 33 blocs) —
     * bord organique, borné à ±edgeNoise.
     */
    private boolean isInCenterBlob(int x, int z) {
        long distSq = (long) x * x + (long) z * z;
        long outerMax = (long) (this.centerRadius + this.centerEdgeNoise);
        if (distSq > outerMax * outerMax) {
            return false; // hors de portée même avec le bruit max (early-exit)
        }
        if (this.centerEdgeNoise == 0) {
            return true; // cercle net (distSq <= radius² garanti par le test précédent)
        }
        long innerMin = (long) (this.centerRadius - this.centerEdgeNoise);
        if (distSq <= innerMin * innerMin) {
            return true; // cœur garanti, pas besoin d'évaluer le bruit
        }
        double noise = 0.65 * valueNoise(x / 96.0, z / 96.0, 0xB0BB1E5L)
                + 0.35 * valueNoise(x / 33.0, z / 33.0, 0xB0BB1E5L * 31L + 7L);
        double wobbly = this.centerRadius + this.centerEdgeNoise * Math.max(-1.0, Math.min(1.0, noise));
        return distSq <= (long) (wobbly * wobbly);
    }

    /**
     * Bruit de valeur 2 octaves (grilles 64 et 23 blocs), interpolation
     * smoothstep — offset de warp dans [-amplitude, amplitude].
     */
    private int warpOffset(int x, int z, long salt) {
        double n = 0.7 * valueNoise(x / 64.0, z / 64.0, salt)
                + 0.3 * valueNoise(x / 23.0, z / 23.0, salt * 31L + 17L);
        return (int) Math.round(this.warpAmplitude * Math.max(-1.0, Math.min(1.0, n)));
    }

    private double valueNoise(double px, double pz, long salt) {
        int x0 = (int) Math.floor(px);
        int z0 = (int) Math.floor(pz);
        double fx = px - x0;
        double fz = pz - z0;
        double sx = fx * fx * (3.0 - 2.0 * fx);
        double sz = fz * fz * (3.0 - 2.0 * fz);
        double v00 = cornerValue(x0, z0, salt);
        double v10 = cornerValue(x0 + 1, z0, salt);
        double v01 = cornerValue(x0, z0 + 1, salt);
        double v11 = cornerValue(x0 + 1, z0 + 1, salt);
        double a = v00 + (v10 - v00) * sx;
        double b = v01 + (v11 - v01) * sx;
        return a + (b - a) * sz; // [-1, 1]
    }

    private double cornerValue(int cx, int cz, long salt) {
        return ((mix(cx, cz, salt) & 0xFFFF) / 32767.5) - 1.0;
    }

    /** Mix 64 bits type SplitMix64, déterministe par (seed, cellule, salt). */
    private long mix(long cx, long cz, long salt) {
        long h = this.seed ^ salt ^ (cx * 0x9E3779B97F4A7C15L) ^ (cz * 0xC2B2AE3D27D4EB4FL);
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        return h ^ (h >>> 31);
    }

    // ------------------------------------------------------------------
    // Overrides WorldChunkManager (aucun ne doit toucher aux GenLayers null)
    // ------------------------------------------------------------------

    /** Biomes de spawn valides : notre whitelist (sinon la recherche de spawn vanilla échouerait). */
    @Override
    public List<BiomeBase> a() {
        return this.spawnBiomes;
    }

    @Override
    public BiomeBase getBiome(BlockPosition blockposition, BiomeBase fallback) {
        return biomeAt(blockposition.getX(), blockposition.getZ());
    }

    @Override
    public float[] getWetness(float[] afloat, int x, int z, int width, int height) {
        if (afloat == null || afloat.length < width * height) {
            afloat = new float[width * height];
        }
        for (int i = 0; i < width * height; i++) {
            float wetness = (float) biomeAt(x + i % width, z + i / width).h() / 65536.0F;
            afloat[i] = wetness > 1.0F ? 1.0F : wetness;
        }
        return afloat;
    }

    /** Résolution 1:4 (coordonnées en unités de 4 blocs), utilisée pour les hauteurs de terrain. */
    @Override
    public BiomeBase[] getBiomes(BiomeBase[] abiomebase, int x, int z, int width, int height) {
        if (abiomebase == null || abiomebase.length < width * height) {
            abiomebase = new BiomeBase[width * height];
        }
        for (int i = 0; i < width * height; i++) {
            int blockX = ((x + i % width) << 2) + 2;
            int blockZ = ((z + i / width) << 2) + 2;
            abiomebase[i] = biomeAt(blockX, blockZ);
        }
        return abiomebase;
    }

    /** Résolution bloc (remplissage des chunks, décoration). Le cache vanilla est inutile ici. */
    @Override
    public BiomeBase[] a(BiomeBase[] abiomebase, int x, int z, int width, int height, boolean useCache) {
        if (abiomebase == null || abiomebase.length < width * height) {
            abiomebase = new BiomeBase[width * height];
        }
        for (int i = 0; i < width * height; i++) {
            abiomebase[i] = biomeAt(x + i % width, z + i / width);
        }
        return abiomebase;
    }

    /** Zone entièrement composée de biomes autorisés ? (structures, spawn) */
    @Override
    public boolean a(int x, int z, int radius, List<BiomeBase> list) {
        int minX = x - radius >> 2;
        int minZ = z - radius >> 2;
        int maxX = x + radius >> 2;
        int maxZ = z + radius >> 2;
        for (int bx = minX; bx <= maxX; bx++) {
            for (int bz = minZ; bz <= maxZ; bz++) {
                if (!list.contains(biomeAt(bx << 2, bz << 2))) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Cherche une position dont le biome est dans la liste (recherche de spawn). */
    @Override
    public BlockPosition a(int x, int z, int radius, List<BiomeBase> list, Random random) {
        int minX = x - radius >> 2;
        int minZ = z - radius >> 2;
        int maxX = x + radius >> 2;
        int maxZ = z + radius >> 2;
        BlockPosition result = null;
        int matches = 0;
        for (int bz = minZ; bz <= maxZ; bz++) {
            for (int bx = minX; bx <= maxX; bx++) {
                if (list.contains(biomeAt(bx << 2, bz << 2))) {
                    matches++;
                    if (result == null || random.nextInt(matches) == 0) {
                        result = new BlockPosition(bx << 2, 0, bz << 2);
                    }
                }
            }
        }
        return result;
    }

    @Override
    public void b() {
        // pas de cache à nettoyer
    }
}
