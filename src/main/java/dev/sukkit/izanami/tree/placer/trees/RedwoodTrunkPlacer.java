package dev.sukkit.izanami.tree.placer.trees;

import dev.sukkit.izanami.tree.placer.BlockPos;
import dev.sukkit.izanami.tree.placer.FoliagePlacer;
import dev.sukkit.izanami.tree.placer.Mth;
import dev.sukkit.izanami.tree.placer.TreeConfig;
import dev.sukkit.izanami.tree.placer.TrunkPlacer;
import net.minecraft.server.v1_8_R3.IBlockData;
import net.minecraft.server.v1_8_R3.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.BiConsumer;

/**
 * Porté depuis Crystallite (RedwoodTrunkPlacer) : tronc large avec racines
 * évasées (scaling par anneau) et branches-buissons dans la partie basse.
 */
public class RedwoodTrunkPlacer extends TrunkPlacer {

    private final int trunkWidth;
    private final int branchChance;

    public RedwoodTrunkPlacer(int baseHeight, int heightRandA, int heightRandB,
                              int trunkWidth, int branchChance) {
        super(baseHeight, heightRandA, heightRandB);
        this.trunkWidth = trunkWidth;
        this.branchChance = branchChance;
    }

    @Override
    public List<FoliagePlacer.FoliageAttachment> placeTrunk(
            World level,
            BiConsumer<BlockPos, IBlockData> blockSetter,
            Random random,
            int height,
            BlockPos startPos,
            TreeConfig config) {

        List<FoliagePlacer.FoliageAttachment> attachments = new ArrayList<>();

        // baseHeight = 55%-70% du tronc (partie sans feuillage)
        int baseHeight = (int) (height * (0.55F + random.nextFloat() * 0.15F));

        // Facteurs de scaling : chaque "anneau" autour du centre a une hauteur proportionnelle
        double[] scalingFactors = new double[]{
            0.75 + random.nextDouble() * 0.15,   // dist=1 : 75-90% de la hauteur
            0.45 + random.nextDouble() * 0.15,   // dist=2 : 45-60%
            0.20 + random.nextDouble() * 0.10,   // dist=3 : 20-30%
            0.08 + random.nextDouble() * 0.07,   // dist=4 : 8-15% (coins élargis)
            0.03 + random.nextDouble() * 0.04    // dist=5 : 3-7%  (racines évasées)
        };

        for (int x = -trunkWidth; x <= trunkWidth; x++) {
            for (int z = -trunkWidth; z <= trunkWidth; z++) {
                int dist = Math.abs(x) + Math.abs(z);

                if (dist > trunkWidth + 2) {
                    continue;
                }

                int heightHere = height - 2;
                if (dist == 1) {
                    heightHere = (int) (height * scalingFactors[0]);
                } else if (dist == 2) {
                    heightHere = (int) (height * scalingFactors[1]);
                } else if (dist == 3) {
                    heightHere = (int) (height * scalingFactors[2]);
                } else if (dist == 4) {
                    heightHere = (int) (height * scalingFactors[3]);
                } else if (dist == 5) {
                    heightHere = (int) (height * scalingFactors[4]);
                }

                heightHere += random.nextInt(3); // ±3 blocs pour l'irrégularité

                for (int y = 0; y < heightHere; y++) {
                    BlockPos local = startPos.offset(x, y, z);
                    TrunkPlacer.placeLog(level, blockSetter, random, local, config);

                    // Branches latérales (buissons) sur les anneaux extérieurs, partie basse
                    if (dist > 0 && dist <= 3
                            && y > 8
                            && y < (baseHeight - 3)
                            && random.nextInt(branchChance) == 0) {

                        double theta;
                        if (x == 0 && z == 0) {
                            if (y < 12) {
                                continue;
                            }
                            theta = Math.PI * random.nextDouble() * 2;
                        } else {
                            double angleFromCenter = Math.atan2(x, z);
                            theta = angleFromCenter + (Math.PI * (random.nextDouble() * 0.5 - 0.25));
                        }

                        float heightRatio = (float) y / baseHeight;
                        int branchLength = (int) (2 + heightRatio * 4) + random.nextInt(2);

                        BlockPos branchPos = local;
                        for (int i = 0; i < branchLength; i++) {
                            branchPos = local.offset(
                                Mth.floor(Math.cos(theta) * i),
                                i / 2,
                                Mth.floor(Math.sin(theta) * i)
                            );
                            TrunkPlacer.placeLog(level, blockSetter, random, branchPos, config);
                        }

                        attachments.add(new FoliagePlacer.FoliageAttachment(branchPos, 0, false));
                    }
                }

                // Attache principale au sommet du centre
                if (x == 0 && z == 0) {
                    attachments.add(new FoliagePlacer.FoliageAttachment(
                        startPos.above(heightHere), 0, true
                    ));
                }
            }
        }

        return attachments;
    }
}
