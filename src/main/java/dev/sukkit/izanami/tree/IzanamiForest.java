package dev.sukkit.izanami.tree;

import net.minecraft.server.v1_8_R3.BiomeBase;
import net.minecraft.server.v1_8_R3.BlockPosition;
import net.minecraft.server.v1_8_R3.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Forêt composée de plusieurs types d'arbres (Java ou schematic), chacun avec
 * son propre espacement et sa propre densité.
 * <p>
 * Placement par grille jitterée déterministe (seed monde) : chaque type a une
 * grille de cellules de {@code spacing} blocs, avec au plus un candidat par
 * cellule à une position pseudo-aléatoire. L'espacement minimum est donc
 * respecté <b>y compris à travers les frontières de chunks</b>, sans aucun
 * état partagé — chaque chunk place uniquement les candidats qui tombent dans
 * sa fenêtre de décoration (16x16 décalée de +8, convention vanilla
 * anti-cascade).
 */
public final class IzanamiForest {

    private final List<Entry> entries;

    private IzanamiForest(List<Entry> entries) {
        this.entries = entries;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Place les candidats de la fenêtre de décoration de ce chunk.
     * Appelé par le décorateur du biome (main thread).
     *
     * @param decoPos position de décoration vanilla (coin du chunk, y=0)
     * @param owner   biome propriétaire : un candidat n'est placé que si le
     *                biome du monde à sa position est celui-ci (les forêts ne
     *                débordent pas sur les biomes voisins)
     */
    public void decorate(World world, Random random, BlockPosition decoPos, BiomeBase owner) {
        int minX = decoPos.getX() + 8;
        int minZ = decoPos.getZ() + 8;
        long worldSeed = world.getSeed();

        for (int entryIndex = 0; entryIndex < this.entries.size(); entryIndex++) {
            Entry entry = this.entries.get(entryIndex);
            int spacing = entry.spacing;
            long salt = worldSeed ^ (0xF0BE57L + entryIndex * 0x9E3779B97F4A7C15L);

            int gx0 = Math.floorDiv(minX, spacing);
            int gx1 = Math.floorDiv(minX + 15, spacing);
            int gz0 = Math.floorDiv(minZ, spacing);
            int gz1 = Math.floorDiv(minZ + 15, spacing);

            for (int gx = gx0; gx <= gx1; gx++) {
                for (int gz = gz0; gz <= gz1; gz++) {
                    long hash = mix(gx, gz, salt);
                    int px = gx * spacing + (int) Math.floorMod(hash, spacing);
                    int pz = gz * spacing + (int) Math.floorMod(hash >>> 32, spacing);
                    if (px < minX || px > minX + 15 || pz < minZ || pz > minZ + 15) {
                        continue; // ce candidat appartient à la fenêtre d'un autre chunk
                    }
                    if (entry.chance < 1.0
                            && ((mix(gx, gz, salt + 1) & 0xFFFF) / 65536.0) >= entry.chance) {
                        continue;
                    }
                    BlockPosition column = new BlockPosition(px, 0, pz);
                    if (world.getBiome(column) != owner) {
                        continue;
                    }
                    entry.tree.place(world, random, world.getHighestBlockYAt(column));
                }
            }
        }
    }

    private static long mix(long cx, long cz, long salt) {
        long h = salt ^ (cx * 0x9E3779B97F4A7C15L) ^ (cz * 0xC2B2AE3D27D4EB4FL);
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        return h ^ (h >>> 31);
    }

    private static final class Entry {
        final IzanamiTree tree;
        final int spacing;
        final double chance;

        Entry(IzanamiTree tree, int spacing, double chance) {
            this.tree = tree;
            this.spacing = spacing;
            this.chance = chance;
        }
    }

    public static final class Builder {
        private final List<Entry> entries = new ArrayList<>();

        /**
         * @param spacing taille de cellule (blocs) : distance typique entre
         *                deux arbres de ce type (minimum 4)
         * @param chance  probabilité qu'un candidat de cellule soit placé (0..1]
         */
        public Builder add(IzanamiTree tree, int spacing, double chance) {
            if (tree == null) throw new IllegalArgumentException("tree null");
            if (spacing < 4) throw new IllegalArgumentException("spacing < 4");
            if (chance <= 0.0 || chance > 1.0) throw new IllegalArgumentException("chance hors ]0,1] : " + chance);
            this.entries.add(new Entry(tree, spacing, chance));
            return this;
        }

        public IzanamiForest build() {
            if (this.entries.isEmpty()) {
                throw new IllegalStateException("Foret sans aucun type d'arbre");
            }
            return new IzanamiForest(Collections.unmodifiableList(new ArrayList<>(this.entries)));
        }
    }
}
