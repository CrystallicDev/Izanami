package dev.sukkit.izanami.gen;

import dev.sukkit.izanami.custom.CustomBlock;
import dev.sukkit.izanami.custom.CustomBlockRegistry;
import net.minecraft.server.v1_8_R3.Block;
import net.minecraft.server.v1_8_R3.BlockPosition;
import net.minecraft.server.v1_8_R3.Blocks;
import net.minecraft.server.v1_8_R3.IBlockData;
import net.minecraft.server.v1_8_R3.WorldServer;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_8_R3.CraftWorld;
import org.bukkit.generator.BlockPopulator;

import java.util.Random;

/**
 * Génère des touffes de bambou dans les biomes jungle : quelques tiges (hôte
 * nether_brick_fence) de hauteur variable, coiffées d'une couronne de feuilles
 * (hôte fleur). Le rendu custom est filtré côté client sur les biomes jungle,
 * donc les hôtes vanilla n'apparaissent nulle part ailleurs.
 * <p>
 * Placement par grille jitterée déterministe (seed monde), même principe que
 * {@link dev.sukkit.izanami.tree.IzanamiForest} : espacement respecté à travers
 * les frontières de chunks sans état partagé.
 */
public final class BambooPopulator extends BlockPopulator {

    private final IBlockData stalk;
    private final IBlockData leaves;
    private final int spacing;
    private final double chance;

    private BambooPopulator(IBlockData stalk, IBlockData leaves, int spacing, double chance) {
        this.stalk = stalk;
        this.leaves = leaves;
        this.spacing = spacing;
        this.chance = chance;
    }

    /** @return le populator, ou null si les blocs bambou ne sont pas enregistrés */
    public static BambooPopulator create(CustomBlockRegistry registry, int spacing, double chance) {
        CustomBlock stalk = registry.get("bamboo_stalk");
        CustomBlock leaves = registry.get("bamboo_leaves");
        if (stalk == null || leaves == null) {
            return null;
        }
        return new BambooPopulator(
                Block.getById(stalk.getBlockId()).fromLegacyData(stalk.getMeta()),
                Block.getById(leaves.getBlockId()).fromLegacyData(leaves.getMeta()),
                Math.max(4, spacing), Math.max(0.0, Math.min(1.0, chance)));
    }

    @Override
    public void populate(World world, Random random, Chunk chunk) {
        WorldServer handle = ((CraftWorld) world).getHandle();
        int minX = (chunk.getX() << 4) + 8;
        int minZ = (chunk.getZ() << 4) + 8;
        long salt = world.getSeed() ^ 0xBA_9B_00L;

        int gx0 = Math.floorDiv(minX, this.spacing);
        int gx1 = Math.floorDiv(minX + 15, this.spacing);
        int gz0 = Math.floorDiv(minZ, this.spacing);
        int gz1 = Math.floorDiv(minZ + 15, this.spacing);

        for (int gx = gx0; gx <= gx1; gx++) {
            for (int gz = gz0; gz <= gz1; gz++) {
                long hash = mix(gx, gz, salt);
                int px = gx * this.spacing + (int) Math.floorMod(hash, this.spacing);
                int pz = gz * this.spacing + (int) Math.floorMod(hash >>> 32, this.spacing);
                if (px < minX || px > minX + 15 || pz < minZ || pz > minZ + 15) {
                    continue; // candidat d'une autre fenêtre de chunk
                }
                if (this.chance < 1.0 && (mix(gx, gz, salt + 1) & 0xFFFF) / 65536.0 >= this.chance) {
                    continue;
                }
                if (!isJungle(handle, px, pz)) {
                    continue;
                }
                // touffe : 2 à 4 tiges autour du point, hauteurs variées
                int stalks = 2 + (int) (mix(gx, gz, salt + 2) & 3);
                for (int i = 0; i < stalks; i++) {
                    long h = mix(gx * 7L + i, gz * 13L, salt + 3);
                    int sx = px + (int) (h & 3) - 1;
                    int sz = pz + (int) ((h >>> 2) & 3) - 1;
                    int height = 4 + (int) ((h >>> 8) % 6); // 4..9
                    placeStalk(handle, sx, sz, height);
                }
            }
        }
    }

    private void placeStalk(WorldServer handle, int x, int z, int height) {
        // vrai sol herbe/terre/sable : sous la canopée, getHighestBlockYAt vise
        // les feuilles — on redescend jusqu'au premier sol exposé
        int topY = handle.getHighestBlockYAt(new BlockPosition(x, 0, z)).getY();
        int groundY = -1;
        for (int y = topY; y >= Math.max(5, topY - 30); y--) {
            Block b = handle.getType(new BlockPosition(x, y, z)).getBlock();
            if (b == Blocks.GRASS || b == Blocks.DIRT || b == Blocks.SAND) {
                groundY = y;
                break;
            }
        }
        if (groundY < 0 || handle.getType(new BlockPosition(x, groundY + 1, z)).getBlock() != Blocks.AIR) {
            return; // pas de sol exposé (sous l'eau, enterré...)
        }
        int placed = 0;
        for (int y = groundY + 1; y < groundY + 1 + height; y++) {
            BlockPosition pos = new BlockPosition(x, y, z);
            if (handle.getType(pos).getBlock() != Blocks.AIR) {
                break; // butte contre la canopée / un relief
            }
            handle.setTypeAndData(pos, this.stalk, 2);
            placed++;
        }
        if (placed > 0) {
            BlockPosition top = new BlockPosition(x, groundY + 1 + placed, z);
            if (handle.getType(top).getBlock() == Blocks.AIR) {
                handle.setTypeAndData(top, this.leaves, 2); // couronne (rien si canopée juste au-dessus)
            }
        }
    }

    private static boolean isJungle(WorldServer handle, int x, int z) {
        net.minecraft.server.v1_8_R3.BiomeBase biome = handle.getBiome(new BlockPosition(x, 0, z));
        return biome != null && biome.ah != null
                && biome.ah.toLowerCase(java.util.Locale.ROOT).contains("jungle");
    }

    private static long mix(long cx, long cz, long salt) {
        long h = salt ^ (cx * 0x9E3779B97F4A7C15L) ^ (cz * 0xC2B2AE3D27D4EB4FL);
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        return h ^ (h >>> 31);
    }
}
