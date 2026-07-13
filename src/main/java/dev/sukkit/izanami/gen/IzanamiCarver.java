package dev.sukkit.izanami.gen;

import dev.sukkit.izanami.world.CaveSettings;
import dev.sukkit.izanami.world.RiverSettings;
import dev.sukkit.izanami.world.UndergroundRiverSettings;
import net.minecraft.server.v1_8_R3.Block;
import net.minecraft.server.v1_8_R3.Blocks;
import net.minecraft.server.v1_8_R3.ChunkSnapshot;
import net.minecraft.server.v1_8_R3.IBlockData;
import net.minecraft.server.v1_8_R3.IChunkProvider;
import net.minecraft.server.v1_8_R3.Material;
import net.minecraft.server.v1_8_R3.World;
import net.minecraft.server.v1_8_R3.WorldGenBase;

/**
 * Carver Izanami : cavernes à bruit (cheese/spaghetti/noodle), rivières,
 * gouffres et géodes — remplace WorldGenCaves/WorldGenCanyon. Bruit 3D
 * échantillonné sur grille 4x4x4 puis interpolé trilinéairement (~4k évals par
 * chunk). Garde-fous : croûte de surface préservée, pas de carve sous un plan
 * d'eau, lave sous y=10. Déterministe par seed.
 */
public final class IzanamiCarver extends WorldGenBase {

    private static final int GRID = 4;              // pas de la grille (x, y, z)
    private static final int GX = 5, GY = 33, GZ = 5; // échantillons par chunk (0..16 / 0..128)

    /** Sel du bruit de tracé des rivières souterraines (partagé avec IzanamiCaveBiomeMap). */
    private static final long UG_RIVER_SALT = 0xBEDCA7EL;
    private static final long UG_WIDTH_SALT = 0x1D7A7EL;  // variation de largeur
    private static final long UG_POOL_SALT = 0x900FBEEL;  // élargissements en lacs
    private static final long UG_FUNNEL_SALT = 0xF0AAE1L; // irrégularité des puits
    private static final long UG_WARP_X_SALT = 0x3A2B1L;  // domain warp X (méandres organiques)
    private static final long UG_WARP_Z_SALT = 0x7C4D9L;  // domain warp Z

    private final long seed;
    private final CaveSettings caves;
    private final RiverSettings rivers;
    private final UndergroundRiverSettings ugRivers;
    private final dev.sukkit.izanami.world.SinkholeSettings sinkholes;
    private final dev.sukkit.izanami.world.GeodeSettings geodes;
    /** {coquille calcite, améthyste, cluster, petit/moyen/grand bourgeon} — null = géodes off. */
    private final IBlockData[] geodeBlocks;
    private final CaveZoneMap zoneMap; // nullable
    private final int ugWaterLevel;
    private final int sinkholeFloorY;
    private final boolean carveCaves;

    // plages dérivées de la hauteur moyenne du sol (terrain.base-height)
    private final int waterTop;      // dernier bloc d'eau (vanilla 62)
    private final int cheeseTop;
    private final int cheeseFadeFrom;
    private final int spaghettiTop;
    private final int noodleTop;
    // seuils dérivés de caves.openness (1.0 = valeurs historiques)
    private final double cheeseThreshold;
    private final double spaghettiThresholdSq;
    private final double noodleThresholdSq;

    private final IBlockData air = Blocks.AIR.getBlockData();
    private final IBlockData lava = Blocks.LAVA.getBlockData();
    private final IBlockData water = Blocks.WATER.getBlockData();
    private final IBlockData sand = Blocks.SAND.getBlockData();
    private final IBlockData gravel = Blocks.GRAVEL.getBlockData();
    private final IBlockData stone = Blocks.STONE.getBlockData();

    public IzanamiCarver(long seed, CaveSettings caves, RiverSettings rivers,
                         UndergroundRiverSettings ugRivers,
                         dev.sukkit.izanami.world.SinkholeSettings sinkholes,
                         dev.sukkit.izanami.world.GeodeSettings geodes, IBlockData[] geodeBlocks,
                         CaveZoneMap zoneMap, int baseHeight) {
        this.seed = seed;
        this.caves = caves;
        this.rivers = rivers;
        this.ugRivers = ugRivers;
        this.sinkholes = sinkholes;
        this.geodes = geodes;
        this.geodeBlocks = geodeBlocks;
        this.zoneMap = zoneMap;
        this.sinkholeFloorY = sinkholes.resolveFloorY(baseHeight);
        this.ugWaterLevel = ugRivers.resolveWaterLevel(baseHeight);
        this.carveCaves = caves.getStyle() == CaveSettings.Style.MODERN
                && (caves.hasCheese() || caves.hasSpaghetti() || caves.hasNoodle());
        // plages dérivées du plafond des cavernes (caves.max-y, auto = base-height + 24) :
        // à base-height 64 sans max-y on retrouve les plages d'origine 52/34/88/56/62
        int maxY = caves.resolveMaxY(baseHeight);
        this.waterTop = baseHeight - 2;
        this.spaghettiTop = maxY;
        this.cheeseTop = Math.max(24, maxY - 36);
        this.cheeseFadeFrom = this.cheeseTop - 18;
        this.noodleTop = Math.max(24, maxY - 32);
        // openness : abaisse le seuil cheese (salles plus vastes) et élargit les
        // tunnels (le rayon d'un tube varie en racine du seuil, d'où le carré)
        double openness = caves.getOpenness();
        this.cheeseThreshold = 0.44 - 0.08 * (openness - 1.0);
        this.spaghettiThresholdSq = 0.010 * openness * openness;
        this.noodleThresholdSq = 0.0032 * openness * openness;
    }

    @Override
    public void a(IChunkProvider provider, World world, int chunkX, int chunkZ, ChunkSnapshot buffer) {
        if (this.carveCaves) {
            carveNoiseCaves(chunkX, chunkZ, buffer);
        }
        if (this.ugRivers.isEnabled()) {
            // après les cavernes : le lit rebouche celles qui passeraient dessous
            carveUndergroundRivers(chunkX, chunkZ, buffer);
        }
        if (this.sinkholes.isEnabled()) {
            carveSinkholes(chunkX, chunkZ, buffer);
        }
        if (this.geodes.isEnabled() && this.geodeBlocks != null) {
            carveGeodes(chunkX, chunkZ, buffer);
        }
        if (this.zoneMap != null) {
            // en dernier : habille les parois de TOUT ce qui a été creusé
            // (les lacs, eux, sont générés en phase populate : CaveLakePopulator)
            applyCaveBiomePalettes(chunkX, chunkZ, buffer);
        }
        if (this.rivers.isEnabled()) {
            carveRivers(chunkX, chunkZ, buffer);
        }
    }

    // ------------------------------------------------------------------
    // Gouffres (accès de surface)
    // ------------------------------------------------------------------

    private void carveSinkholes(int chunkX, int chunkZ, ChunkSnapshot buffer) {
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;
        int spacing = this.sinkholes.getSpacing();
        int reach = this.sinkholes.getRadius() + 6; // rayon max + évasement + marge
        int gx0 = Math.floorDiv(baseX - reach, spacing);
        int gx1 = Math.floorDiv(baseX + 15 + reach, spacing);
        int gz0 = Math.floorDiv(baseZ - reach, spacing);
        int gz1 = Math.floorDiv(baseZ + 15 + reach, spacing);

        for (int gx = gx0; gx <= gx1; gx++) {
            for (int gz = gz0; gz <= gz1; gz++) {
                long hash = mix(gx, gz, 0x60FF4E51L);
                int px = gx * spacing + (int) Math.floorMod(hash, spacing);
                int pz = gz * spacing + (int) Math.floorMod(hash >>> 32, spacing);

                for (int dx = -reach; dx <= reach; dx++) {
                    int lx = px + dx - baseX;
                    if (lx < 0 || lx > 15) {
                        continue;
                    }
                    for (int dz = -reach; dz <= reach; dz++) {
                        int lz = pz + dz - baseZ;
                        if (lz < 0 || lz > 15) {
                            continue;
                        }
                        double dist = Math.sqrt(dx * dx + dz * dz);
                        // bord bruité : rayon de base modulé par colonne
                        double edge = 1.0 + 0.25 * corner(this.seed, px + dx, 0, pz + dz, 0x60FFED6EL);
                        int top = topSolid(buffer, lx, lz);
                        if (top <= this.sinkholeFloorY + 4) {
                            continue;
                        }
                        for (int y = top; y > this.sinkholeFloorY; y--) {
                            // évasé en haut : rayon plein en surface, resserré au fond
                            double depthFactor = 0.7 + 0.5 * (y - this.sinkholeFloorY)
                                    / (double) (top - this.sinkholeFloorY);
                            if (dist <= this.sinkholes.getRadius() * edge * depthFactor
                                    && buffer.a(lx, y, lz).getBlock() != Blocks.BEDROCK) {
                                buffer.a(lx, y, lz, this.air);
                            }
                        }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Géodes d'améthyste
    // ------------------------------------------------------------------

    private void carveGeodes(int chunkX, int chunkZ, ChunkSnapshot buffer) {
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;
        int spacing = this.geodes.getSpacing();
        int radius = this.geodes.getRadius();
        int reach = radius + 2;
        int gx0 = Math.floorDiv(baseX - reach, spacing);
        int gx1 = Math.floorDiv(baseX + 15 + reach, spacing);
        int gz0 = Math.floorDiv(baseZ - reach, spacing);
        int gz1 = Math.floorDiv(baseZ + 15 + reach, spacing);

        for (int gx = gx0; gx <= gx1; gx++) {
            for (int gz = gz0; gz <= gz1; gz++) {
                long hash = mix(gx, gz, 0x6E0DE5L);
                int px = gx * spacing + (int) Math.floorMod(hash, spacing);
                int pz = gz * spacing + (int) Math.floorMod(hash >>> 24, spacing);
                int yRange = this.geodes.getMaxY() - this.geodes.getMinY() - 2 * radius;
                int py = this.geodes.getMinY() + radius
                        + (int) Math.floorMod(hash >>> 48, Math.max(1, yRange));
                if (px < baseX - reach || px > baseX + 15 + reach
                        || pz < baseZ - reach || pz > baseZ + 15 + reach) {
                    continue;
                }

                // sphère : coquille calcite, couche améthyste, centre creux
                for (int dx = -reach; dx <= reach; dx++) {
                    int lx = px + dx - baseX;
                    if (lx < 0 || lx > 15) {
                        continue;
                    }
                    for (int dz = -reach; dz <= reach; dz++) {
                        int lz = pz + dz - baseZ;
                        if (lz < 0 || lz > 15) {
                            continue;
                        }
                        for (int dy = -reach; dy <= reach; dy++) {
                            int y = py + dy;
                            if (y < 6 || y > 200) {
                                continue;
                            }
                            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                            // bord légèrement bruité par direction
                            double wobble = 0.6 * corner(this.seed, px + dx, y, pz + dz, 0x6E0DE60BL);
                            double r = radius + wobble;
                            if (dist > r || buffer.a(lx, y, lz).getBlock() == Blocks.BEDROCK) {
                                continue;
                            }
                            if (dist <= r - 2.0) {
                                buffer.a(lx, y, lz, this.air);
                            } else if (dist <= r - 1.0) {
                                buffer.a(lx, y, lz, this.geodeBlocks[1]); // améthyste
                            } else {
                                buffer.a(lx, y, lz, this.geodeBlocks[0]); // calcite
                            }
                        }
                    }
                }

                // clusters et bourgeons : uniquement posés à plat sur l'améthyste
                for (int dx = -reach; dx <= reach; dx++) {
                    int lx = px + dx - baseX;
                    if (lx < 0 || lx > 15) {
                        continue;
                    }
                    for (int dz = -reach; dz <= reach; dz++) {
                        int lz = pz + dz - baseZ;
                        if (lz < 0 || lz > 15) {
                            continue;
                        }
                        for (int dy = -reach; dy <= reach; dy++) {
                            int y = py + dy;
                            if (y < 7 || y > 200) {
                                continue;
                            }
                            if (buffer.a(lx, y, lz).getBlock() != Blocks.AIR
                                    || buffer.a(lx, y - 1, lz) != this.geodeBlocks[1]) {
                                continue;
                            }
                            long spotHash = mix(px + dx, ((long) y << 20) ^ (pz + dz), 0xC1057E4L);
                            if ((spotHash & 0xFF) < 90) { // ~35%
                                buffer.a(lx, y, lz, this.geodeBlocks[2 + (int) Math.floorMod(spotHash >>> 8, 4)]);
                            }
                        }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Habillage des cavernes par biome de cave (palette + décorations)
    // ------------------------------------------------------------------

    private void applyCaveBiomePalettes(int chunkX, int chunkZ, ChunkSnapshot buffer) {
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;
        int yMin = Math.max(2, this.zoneMap.getYMin());
        int yMax = this.zoneMap.getYMax();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                dev.sukkit.izanami.api.CaveBiome biome = this.zoneMap.biomeAtColumn(baseX + x, baseZ + z);
                if (biome == null) {
                    continue;
                }
                // décorations par taches (façon vanilla) : hors patch, palette
                // seule (sol/parois/plafond), aucune décoration
                boolean inDecoPatch = true;
                if (biome.getDecorationPatchSpacing() > 0) {
                    int s = biome.getDecorationPatchSpacing();
                    long cellX = Math.floorDiv(baseX + x, s);
                    long cellZ = Math.floorDiv(baseZ + z, s);
                    long patchHash = mix(cellX, cellZ, 0xDECA7C4L);
                    long patchX = cellX * s + Math.floorMod(patchHash, s);
                    long patchZ = cellZ * s + Math.floorMod(patchHash >>> 32, s);
                    long pdx = baseX + x - patchX;
                    long pdz = baseZ + z - patchZ;
                    int r = biome.getDecorationPatchRadius();
                    inDecoPatch = pdx * pdx + pdz * pdz <= (long) r * r;
                }
                for (int y = yMin; y <= yMax; y++) {
                    if (buffer.a(x, y, z).getBlock() != Blocks.AIR) {
                        continue;
                    }
                    // sol / plafond
                    if (biome.getFloorBlock() != null && buffer.a(x, y - 1, z).getBlock() == Blocks.STONE) {
                        buffer.a(x, y - 1, z, biome.getFloorBlock());
                    }
                    if (biome.getCeilingBlock() != null && buffer.a(x, y + 1, z).getBlock() == Blocks.STONE) {
                        buffer.a(x, y + 1, z, biome.getCeilingBlock());
                    }
                    // parois latérales (voisins intra-chunk uniquement)
                    if (biome.getWallBlock() != null) {
                        if (x > 0 && buffer.a(x - 1, y, z).getBlock() == Blocks.STONE) {
                            buffer.a(x - 1, y, z, biome.getWallBlock());
                        }
                        if (x < 15 && buffer.a(x + 1, y, z).getBlock() == Blocks.STONE) {
                            buffer.a(x + 1, y, z, biome.getWallBlock());
                        }
                        if (z > 0 && buffer.a(x, y, z - 1).getBlock() == Blocks.STONE) {
                            buffer.a(x, y, z - 1, biome.getWallBlock());
                        }
                        if (z < 15 && buffer.a(x, y, z + 1).getBlock() == Blocks.STONE) {
                            buffer.a(x, y, z + 1, biome.getWallBlock());
                        }
                    }
                    // décorations (dans l'air, contre sol/plafond), variantes par hash,
                    // colonnes 1..N pour la première variante uniquement
                    if (!inDecoPatch) {
                        continue;
                    }
                    long hash = mix(baseX + x, ((long) y << 20) ^ (baseZ + z), 0xDEC0DEC0L);
                    double roll = (hash & 0xFFFF) / 65536.0;
                    if (roll < biome.getDecorationChance()) {
                        Block below = buffer.a(x, y - 1, z).getBlock();
                        Block above = buffer.a(x, y + 1, z).getBlock();
                        IBlockData[] floorDecos = biome.getFloorDecorations();
                        IBlockData[] ceilingDecos = biome.getCeilingDecorations();
                        // le support doit être un cube plein (isOccluding) : ainsi
                        // un bloc de déco fin (tapis/veine, non-occludant) ne peut
                        // pas en supporter un autre → pas d'empilement
                        if (floorDecos != null && below.isOccluding()) {
                            int variant = (int) Math.floorMod(hash >>> 32, floorDecos.length);
                            int height = variant != 0 ? 1
                                    : 1 + (int) Math.floorMod(hash >>> 16, biome.getFloorDecorationMaxHeight());
                            for (int h = 0; h < height && y + h <= yMax
                                    && buffer.a(x, y + h, z).getBlock() == Blocks.AIR; h++) {
                                buffer.a(x, y + h, z, floorDecos[variant]);
                            }
                        } else if (ceilingDecos != null && above.isOccluding()) {
                            int variant = (int) Math.floorMod(hash >>> 32, ceilingDecos.length);
                            int height = variant != 0 ? 1
                                    : 1 + (int) Math.floorMod(hash >>> 16, biome.getCeilingDecorationMaxHeight());
                            for (int h = 0; h < height && y - h >= yMin
                                    && buffer.a(x, y - h, z).getBlock() == Blocks.AIR; h++) {
                                buffer.a(x, y - h, z, ceilingDecos[variant]);
                            }
                        }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Rivières souterraines (biome de cave "Riviere Souterraine")
    // ------------------------------------------------------------------

    private void carveUndergroundRivers(int chunkX, int chunkZ, ChunkSnapshot buffer) {
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;
        double scale = this.ugRivers.getScale();
        double threshold = this.ugRivers.getWidth() / (scale * 3.25);
        int yWater = this.ugWaterLevel;
        int gap = this.ugRivers.getAirGap();

        double widthVar = this.ugRivers.getWidthVariation();
        double poolInt = this.ugRivers.getPoolIntensity();
        int baseDepth = this.ugRivers.getWaterDepth();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int wx = baseX + x;
                int wz = baseZ + z;
                double ridge = Math.abs(ridgeNoise(this.seed, wx / scale, wz / scale));

                // rayon variable : bruit basse fréquence, [-1,1]
                double widthNoise = valueNoise3(this.seed, wx / (scale * 0.5), 0.0, wz / (scale * 0.5), UG_WIDTH_SALT);
                // lacs : bruit très basse fréquence, partie positive uniquement
                double pool = Math.max(0.0, valueNoise3(this.seed, wx / (scale * 1.3), 0.0, wz / (scale * 1.3), UG_POOL_SALT));
                double localThreshold = threshold * (1.0 + widthVar * widthNoise + poolInt * pool * pool * 2.0);
                localThreshold = Math.max(threshold * 0.25, localThreshold); // ne jamais fermer la rivière
                if (ridge >= localThreshold) {
                    continue;
                }

                // Section transversale ARRONDIE (inspirée du seuillage lisse 3D
                // de WWOO/1.18) : coordonnée radiale normalisée (0 centre .. 1 bord),
                // profil demi-cercle sqrt(1-r²) → bol au fond + dôme au-dessus,
                // ligne d'eau plate au centre. Bien plus sphérique qu'une dalle.
                double radial = ridge / localThreshold;              // 0..1
                double round = Math.sqrt(Math.max(0.0, 1.0 - radial * radial)); // 1 centre .. 0 bord
                int depth = baseDepth + (int) Math.round(pool * poolInt * 2.5);  // lacs plus profonds
                int dome = gap + (int) Math.round(pool * 3.0);                   // headroom + lacs plus hauts
                int floor = yWater - Math.max(1, (int) Math.round(depth * round));
                int ceiling = yWater + Math.max(1, (int) Math.round(dome * round));

                // ne jamais percer la surface le long du tunnel (les accès sont les chutes)
                if (topSolid(buffer, x, z) <= ceiling + 4) {
                    continue;
                }

                for (int y = ceiling; y > yWater; y--) {
                    if (buffer.a(x, y, z).getBlock() != Blocks.BEDROCK) {
                        buffer.a(x, y, z, this.air);
                    }
                }
                for (int y = yWater; y > floor; y--) {
                    buffer.a(x, y, z, this.water);
                }
                buffer.a(x, floor, z,
                        (mix(wx, wz, 0x5EDBEDL) & 3) == 0 ? this.gravel : this.sand);
                if (buffer.a(x, floor - 1, z).getBlock() == Blocks.AIR) {
                    buffer.a(x, floor - 1, z, this.stone);
                }
            }
        }

        carveWaterfalls(chunkX, chunkZ, buffer, threshold, scale, yWater, gap);
    }

    /**
     * Chutes d'entrée : points déterministes le long du tracé (grille jitterée
     * de waterfall-spacing). Le puits est un <b>double cône irrégulier</b> —
     * large en surface et au niveau de la rivière, resserré en un goulet au
     * milieu (deux entonnoirs opposés), avec un bord bruité. Un mince courant
     * d'eau tombe en son centre. Chaque chunk creuse sa part (les candidats
     * sont recalculés à l'identique par les chunks voisins ; chaque colonne est
     * lue/écrite localement, donc cross-chunk sûr).
     */
    private void carveWaterfalls(int chunkX, int chunkZ, ChunkSnapshot buffer,
                                 double threshold, double scale, int yWater, int gap) {
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;
        int spacing = this.ugRivers.getWaterfallSpacing();
        int maxR = this.ugRivers.getWaterfallRadius();
        int reach = maxR + 4; // rayon du cône (large) + dérive du raffinement + garde
        int gx0 = Math.floorDiv(baseX - reach, spacing);
        int gx1 = Math.floorDiv(baseX + 15 + reach, spacing);
        int gz0 = Math.floorDiv(baseZ - reach, spacing);
        int gz1 = Math.floorDiv(baseZ + 15 + reach, spacing);

        for (int gx = gx0; gx <= gx1; gx++) {
            for (int gz = gz0; gz <= gz1; gz++) {
                // Projection du point de chute SUR la rivière : minimum de |ridge|
                // dans la cellule (passe grossière pas 8, puis raffinement pas 1).
                double best = Double.MAX_VALUE;
                int px = 0;
                int pz = 0;
                for (int sx = gx * spacing; sx < (gx + 1) * spacing; sx += 8) {
                    for (int sz = gz * spacing; sz < (gz + 1) * spacing; sz += 8) {
                        double r = Math.abs(ridgeNoise(this.seed, sx / scale, sz / scale));
                        if (r < best) {
                            best = r;
                            px = sx;
                            pz = sz;
                        }
                    }
                }
                for (int dx = -4; dx <= 4; dx++) {
                    for (int dz = -4; dz <= 4; dz++) {
                        double r = Math.abs(ridgeNoise(this.seed, (px + dx) / scale, (pz + dz) / scale));
                        if (r < best) {
                            best = r;
                            px = px + dx;
                            pz = pz + dz;
                        }
                    }
                }
                if (best >= threshold * 0.6) {
                    continue; // pas de rivière souterraine dans cette cellule
                }
                if (px < baseX - reach || px > baseX + 15 + reach
                        || pz < baseZ - reach || pz > baseZ + 15 + reach) {
                    continue; // le cône ne touche pas ce chunk
                }

                carveFunnelShaft(buffer, baseX, baseZ, px, pz, yWater, gap, maxR);
            }
        }
    }

    /**
     * Creuse un puits en double cône (entonnoir haut + entonnoir bas, goulet au
     * milieu) centré sur ({@code px, pz}), du sol de chaque colonne jusqu'à la
     * rivière, avec un bord irrégulier. Place un mince courant d'eau au centre.
     */
    private void carveFunnelShaft(ChunkSnapshot buffer, int baseX, int baseZ, int px, int pz,
                                  int yWater, int gap, int maxR) {
        double throat = 0.8; // rayon du goulet au milieu

        for (int dx = -maxR - 2; dx <= maxR + 2; dx++) {
            int lx = px + dx - baseX;
            if (lx < 0 || lx > 15) {
                continue;
            }
            for (int dz = -maxR - 2; dz <= maxR + 2; dz++) {
                int lz = pz + dz - baseZ;
                if (lz < 0 || lz > 15) {
                    continue;
                }
                int wx = px + dx;
                int wz = pz + dz;
                double horizDist = Math.sqrt(dx * dx + dz * dz);
                if (horizDist > maxR + 2) {
                    continue;
                }
                int colTop = topSolid(buffer, lx, lz);
                if (colTop <= yWater + gap + 3) {
                    continue; // pas assez de roche au-dessus de la rivière
                }
                int height = colTop - yWater;

                // creuse l'air en double cône — puits SEC : aucune eau injectée.
                // L'eau de surface (mer/lac au-dessus du puits) s'y déverse
                // naturellement (voir WaterSettlePopulator, drainage par le bas).
                for (int y = colTop; y > yWater; y--) {
                    double t = (double) (y - yWater) / height;          // 0 (rivière) .. 1 (surface)
                    double hourglass = throat + (maxR - throat) * Math.abs(2.0 * t - 1.0);
                    double wobble = 1.6 * valueNoise3(this.seed, wx / 6.0, y / 5.0, wz / 6.0, UG_FUNNEL_SALT);
                    if (horizDist <= hourglass + wobble
                            && buffer.a(lx, y, lz).getBlock() != Blocks.BEDROCK) {
                        buffer.a(lx, y, lz, this.air);
                    }
                }
            }
        }
    }

    /**
     * Bruit du tracé des rivières souterraines — partagé avec
     * {@link IzanamiCaveBiomeMap}. Domain warping : les coordonnées
     * d'échantillonnage sont décalées par un bruit basse fréquence, ce qui tord
     * le réseau de vallées en méandres organiques (façon perlin worm) au lieu
     * d'un motif régulier. Déterministe et cross-chunk (pas de simulation).
     */
    static double ridgeNoise(long seed, double x, double z) {
        double warpX = valueNoise3(seed, x * 0.5, 0.0, z * 0.5, UG_WARP_X_SALT);
        double warpZ = valueNoise3(seed, x * 0.5 + 5.2, 0.0, z * 0.5 + 1.7, UG_WARP_Z_SALT);
        double wx = x + 0.45 * warpX;
        double wz = z + 0.45 * warpZ;
        return (valueNoise3(seed, wx, 0.0, wz, UG_RIVER_SALT)
                + 0.5 * valueNoise3(seed, wx * 2.31, 0.0, wz * 2.31, UG_RIVER_SALT * 31L + 13L)) / 1.5;
    }

    // ------------------------------------------------------------------
    // Cavernes à bruit
    // ------------------------------------------------------------------

    private void carveNoiseCaves(int chunkX, int chunkZ, ChunkSnapshot buffer) {
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;

        // Échantillonnage des champs sur la grille du chunk.
        double[] cheese = this.caves.hasCheese() ? new double[GX * GY * GZ] : null;
        double[] spag1 = this.caves.hasSpaghetti() ? new double[GX * GY * GZ] : null;
        double[] spag2 = this.caves.hasSpaghetti() ? new double[GX * GY * GZ] : null;
        double[] noodle1 = this.caves.hasNoodle() ? new double[GX * GY * GZ] : null;
        double[] noodle2 = this.caves.hasNoodle() ? new double[GX * GY * GZ] : null;

        for (int gx = 0; gx < GX; gx++) {
            double wx = baseX + gx * GRID;
            for (int gz = 0; gz < GZ; gz++) {
                double wz = baseZ + gz * GRID;
                int column = (gx * GZ + gz) * GY;
                for (int gy = 0; gy < GY; gy++) {
                    double wy = gy * GRID;
                    int idx = column + gy;
                    if (cheese != null) {
                        cheese[idx] = fbm3(wx / 90.0, wy / 48.0, wz / 90.0, 0xCEE5EL);
                    }
                    if (spag1 != null) {
                        spag1[idx] = fbm3(wx / 48.0, wy / 32.0, wz / 48.0, 0x5A9E77L);
                        spag2[idx] = fbm3(wx / 48.0, wy / 32.0, wz / 48.0, 0x5A9E77L * 31L + 11L);
                    }
                    if (noodle1 != null) {
                        noodle1[idx] = fbm3(wx / 28.0, wy / 20.0, wz / 28.0, 0x400D1EL);
                        noodle2[idx] = fbm3(wx / 28.0, wy / 20.0, wz / 28.0, 0x400D1EL * 31L + 5L);
                    }
                }
            }
        }

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int ceiling = carveCeiling(buffer, x, z);
                if (ceiling < 6) {
                    continue;
                }
                for (int y = 6; y <= ceiling; y++) {
                    IBlockData current = buffer.a(x, y, z);
                    Block block = current.getBlock();
                    if (block == Blocks.AIR || block == Blocks.BEDROCK
                            || current.getBlock().getMaterial().isLiquid()) {
                        continue;
                    }

                    boolean carve = false;
                    if (cheese != null && y <= this.cheeseTop) {
                        // grandes salles : seuil abaissé en profondeur, fondu vers le haut
                        double v = trilerp(cheese, x, y, z);
                        double threshold = this.cheeseThreshold + Math.max(0, y - this.cheeseFadeFrom) * 0.012;
                        carve = v > threshold;
                    }
                    if (!carve && spag1 != null && y <= this.spaghettiTop) {
                        double a = trilerp(spag1, x, y, z);
                        double b = trilerp(spag2, x, y, z);
                        carve = a * a + b * b < this.spaghettiThresholdSq; // tube autour des intersections
                    }
                    if (!carve && noodle1 != null && y <= this.noodleTop) {
                        double a = trilerp(noodle1, x, y, z);
                        double b = trilerp(noodle2, x, y, z);
                        carve = a * a + b * b < this.noodleThresholdSq;
                    }

                    if (carve) {
                        buffer.a(x, y, z, y < 10 ? this.lava : this.air);
                    }
                }
            }
        }
    }

    /**
     * Plafond de carve d'une colonne : sous la croûte de surface (3 blocs) et
     * sous tout plan d'eau (marge 4 blocs), plafonné à 100.
     */
    private int carveCeiling(ChunkSnapshot buffer, int x, int z) {
        int topSolid = -1;
        int lowestLiquid = Integer.MAX_VALUE;
        for (int y = Math.min(180, this.spaghettiTop + 56); y >= 6; y--) {
            Block block = buffer.a(x, y, z).getBlock();
            if (block == Blocks.AIR) {
                continue;
            }
            if (block.getMaterial().isLiquid()) {
                lowestLiquid = y;
            } else if (topSolid == -1) {
                topSolid = y;
            }
        }
        if (topSolid == -1) {
            return -1;
        }
        int ceiling = Math.min(topSolid - 3, 127); // 127 : limite de la grille de bruit
        if (lowestLiquid != Integer.MAX_VALUE) {
            ceiling = Math.min(ceiling, lowestLiquid - 4);
        }
        return ceiling;
    }

    /** Interpolation trilinéaire d'un champ échantillonné sur la grille du chunk. */
    private static double trilerp(double[] grid, int x, int y, int z) {
        int gx = x >> 2, gy = y >> 2, gz = z >> 2;
        double fx = (x & 3) * 0.25, fy = (y & 3) * 0.25, fz = (z & 3) * 0.25;
        int gx1 = Math.min(gx + 1, GX - 1);
        int gy1 = Math.min(gy + 1, GY - 1);
        int gz1 = Math.min(gz + 1, GZ - 1);

        double c000 = grid[(gx * GZ + gz) * GY + gy], c100 = grid[(gx1 * GZ + gz) * GY + gy];
        double c001 = grid[(gx * GZ + gz1) * GY + gy], c101 = grid[(gx1 * GZ + gz1) * GY + gy];
        double c010 = grid[(gx * GZ + gz) * GY + gy1], c110 = grid[(gx1 * GZ + gz) * GY + gy1];
        double c011 = grid[(gx * GZ + gz1) * GY + gy1], c111 = grid[(gx1 * GZ + gz1) * GY + gy1];

        double x00 = c000 + (c100 - c000) * fx;
        double x01 = c001 + (c101 - c001) * fx;
        double x10 = c010 + (c110 - c010) * fx;
        double x11 = c011 + (c111 - c011) * fx;
        double z0 = x00 + (x01 - x00) * fz;
        double z1 = x10 + (x11 - x10) * fz;
        return z0 + (z1 - z0) * fy;
    }

    // ------------------------------------------------------------------
    // Rivières
    // ------------------------------------------------------------------

    private void carveRivers(int chunkX, int chunkZ, ChunkSnapshot buffer) {
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;
        double scale = this.rivers.getScale();
        // calibré par mesure : largeur de bande (blocs) ~= seuil * 3.25 * scale
        double threshold = this.rivers.getWidth() / (scale * 3.25);
        double bankLimit = threshold * 2.6; // berges larges (~0.8x la largeur du lit de chaque côté)

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int wx = baseX + x;
                int wz = baseZ + z;
                double ridge = Math.abs(fbm2(wx / scale, wz / scale, 0x51E44AL));
                if (ridge >= bankLimit) {
                    continue;
                }

                int topSolid = topSolid(buffer, x, z);
                if (topSolid <= this.waterTop) {
                    continue; // déjà océan/lac : rien à creuser
                }

                if (ridge < threshold) {
                    // lit : fond aplati au centre (smoothstep saturé), entrée douce aux rives
                    double centrality = Math.min(1.0, (1.0 - ridge / threshold) * 1.5);
                    double smooth = centrality * centrality * (3.0 - 2.0 * centrality);
                    int floor = this.waterTop - 1 - (int) Math.round(smooth * this.rivers.getDepth());
                    for (int y = topSolid; y > this.waterTop; y--) {
                        buffer.a(x, y, z, this.air);
                    }
                    for (int y = this.waterTop; y > floor; y--) {
                        buffer.a(x, y, z, this.water);
                    }
                    // lit solide (bouche aussi une éventuelle caverne juste dessous)
                    buffer.a(x, floor, z, (mix(wx, wz, 0x5EDBEDL) & 3) == 0 ? this.gravel : this.sand);
                    if (buffer.a(x, floor - 1, z).getBlock() == Blocks.AIR) {
                        buffer.a(x, floor - 1, z, this.stone);
                    }
                } else {
                    // berges : vallée en S (smoothstep) du bord de l'eau vers le relief,
                    // au lieu d'une rampe linéaire (profil en V)
                    double bankFactor = (ridge - threshold) / (bankLimit - threshold); // 0 rive -> 1 exterieur
                    double smooth = bankFactor * bankFactor * (3.0 - 2.0 * bankFactor);
                    int target = this.waterTop + 1 + (int) Math.round(smooth * (topSolid - this.waterTop - 1));
                    for (int y = topSolid; y > target; y--) {
                        buffer.a(x, y, z, this.air);
                    }
                }
            }
        }
    }

    private static int topSolid(ChunkSnapshot buffer, int x, int z) {
        for (int y = 180; y >= 0; y--) {
            Block block = buffer.a(x, y, z).getBlock();
            if (block != Blocks.AIR && !block.getMaterial().isLiquid()
                    && block.getMaterial() != Material.LEAVES && block.getMaterial() != Material.WOOD) {
                return y;
            }
        }
        return 0;
    }

    // ------------------------------------------------------------------
    // Bruit
    // ------------------------------------------------------------------

    /** FBM 2 octaves, 3D, sortie ~[-1, 1]. */
    private double fbm3(double x, double y, double z, long salt) {
        return (valueNoise3(this.seed, x, y, z, salt)
                + 0.5 * valueNoise3(this.seed, x * 2.17, y * 2.17, z * 2.17, salt * 31L + 7L)) / 1.5;
    }

    /** FBM 2 octaves, 2D, sortie ~[-1, 1]. */
    private double fbm2(double x, double z, long salt) {
        return (valueNoise3(this.seed, x, 0.0, z, salt)
                + 0.5 * valueNoise3(this.seed, x * 2.31, 0.0, z * 2.31, salt * 31L + 13L)) / 1.5;
    }

    private static double valueNoise3(long seed, double px, double py, double pz, long salt) {
        int x0 = (int) Math.floor(px), y0 = (int) Math.floor(py), z0 = (int) Math.floor(pz);
        double fx = px - x0, fy = py - y0, fz = pz - z0;
        double sx = fx * fx * (3.0 - 2.0 * fx);
        double sy = fy * fy * (3.0 - 2.0 * fy);
        double sz = fz * fz * (3.0 - 2.0 * fz);

        double c000 = corner(seed, x0, y0, z0, salt), c100 = corner(seed, x0 + 1, y0, z0, salt);
        double c010 = corner(seed, x0, y0 + 1, z0, salt), c110 = corner(seed, x0 + 1, y0 + 1, z0, salt);
        double c001 = corner(seed, x0, y0, z0 + 1, salt), c101 = corner(seed, x0 + 1, y0, z0 + 1, salt);
        double c011 = corner(seed, x0, y0 + 1, z0 + 1, salt), c111 = corner(seed, x0 + 1, y0 + 1, z0 + 1, salt);

        double x00 = c000 + (c100 - c000) * sx;
        double x10 = c010 + (c110 - c010) * sx;
        double x01 = c001 + (c101 - c001) * sx;
        double x11 = c011 + (c111 - c011) * sx;
        double y0v = x00 + (x10 - x00) * sy;
        double y1v = x01 + (x11 - x01) * sy;
        return y0v + (y1v - y0v) * sz;
    }

    private static double corner(long seed, long x, long y, long z, long salt) {
        long h = seed ^ salt ^ (x * 0x9E3779B97F4A7C15L) ^ (y * 0xC2B2AE3D27D4EB4FL)
                ^ (z * 0x165667B19E3779F9L);
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h ^= h >>> 31;
        return ((h & 0xFFFF) / 32767.5) - 1.0;
    }

    private long mix(long x, long z, long salt) {
        long h = this.seed ^ salt ^ (x * 0x9E3779B97F4A7C15L) ^ (z * 0xC2B2AE3D27D4EB4FL);
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        return h ^ (h >>> 31);
    }
}
