package dev.sukkit.izanami.gen;

import dev.sukkit.izanami.api.CaveBiome;
import dev.sukkit.izanami.api.IzanamiCaveBiomeRegistry;
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
 * Convertit les lacs de lave vanilla des biomes à pools (ex. Lush Caves) en
 * lacs d'eau : formes naturelles vanilla, fond tapissé de leur bloc de fond
 * (argile), pads flottants en surface. Phase populate (le monde réel est
 * lisible) ; flood-fill du corps de lave entier pour éviter tout contact
 * eau/lave statique aux frontières de zone.
 */
public final class CaveLakePopulator extends BlockPopulator {

    private final long seed;
    private final CaveZoneMap zones;

    public CaveLakePopulator(long seed, CaveZoneMap zones) {
        this.seed = seed;
        this.zones = zones;
    }

    @Override
    public void populate(World world, Random random, Chunk chunk) {
        int baseX = chunk.getX() << 4;
        int baseZ = chunk.getZ() << 4;
        WorldServer handle = ((CraftWorld) world).getHandle();
        IBlockData water = Blocks.WATER.getBlockData();
        int yMin = Math.max(6, this.zones.getYMin() - 2);
        int yMax = this.zones.getYMax() + 16;

        // Conversion des lacs de LAVE vanilla en lacs d'eau dans les biomes à
        // pools. Le CORPS DE LAVE ENTIER est converti d'un bloc (flood-fill) :
        // un lac à cheval sur la frontière de zone ne laisse jamais un contact
        // eau/lave sans update (l'eau posée en flag 2 est statique).
        java.util.Set<Long> visited = new java.util.HashSet<>();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                CaveBiome biome = this.zones.biomeAtColumn(baseX + x, baseZ + z);
                if (biome == null || biome.getPoolLiners() == null) {
                    continue;
                }
                for (int y = yMax; y >= yMin; y--) {
                    BlockPosition pos = new BlockPosition(baseX + x, y, baseZ + z);
                    if (visited.contains(key(pos))
                            || handle.getType(pos).getBlock().getMaterial()
                                    != net.minecraft.server.v1_8_R3.Material.LAVA) {
                        continue;
                    }
                    convertLavaBody(handle, biome, pos, water, visited);
                }
            }
        }
    }

    /**
     * Flood-fill 6-connexe du corps de lave (cap 4096 blocs, rayon 24 du
     * départ : un lac vanilla en fait ~16) : tout devient eau, fond argile,
     * pads en surface.
     */
    private void convertLavaBody(WorldServer handle, CaveBiome biome, BlockPosition start,
                                 IBlockData water, java.util.Set<Long> visited) {
        IBlockData[] liners = biome.getPoolLiners();
        java.util.ArrayDeque<BlockPosition> queue = new java.util.ArrayDeque<>();
        queue.add(start);
        visited.add(key(start));
        int converted = 0;

        while (!queue.isEmpty() && converted < 4096) {
            BlockPosition pos = queue.poll();
            if (handle.getType(pos).getBlock().getMaterial()
                    != net.minecraft.server.v1_8_R3.Material.LAVA) {
                continue;
            }
            handle.setTypeAndData(pos, water, 2);
            converted++;

            long spotHash = mix(pos.getX(), ((long) pos.getY() << 20) ^ pos.getZ(), 0xB001DECL);
            Block below = handle.getType(pos.down()).getBlock();
            if (below != Blocks.AIR && below != Blocks.BEDROCK
                    && !below.getMaterial().isLiquid()) {
                handle.setTypeAndData(pos.down(),
                        liners[(int) Math.floorMod(spotHash, liners.length)], 2);
            }
            if (biome.getPoolPad() != null
                    && handle.getType(pos.up()).getBlock() == Blocks.AIR
                    && (spotHash >>> 16 & 0xFF) < 38) { // ~15%
                handle.setTypeAndData(pos.up(), biome.getPoolPad(), 2);
            }

            BlockPosition[] neighbours = {pos.up(), pos.down(), pos.north(), pos.south(),
                    pos.east(), pos.west()};
            for (BlockPosition next : neighbours) {
                if (Math.abs(next.getX() - start.getX()) > 24
                        || Math.abs(next.getZ() - start.getZ()) > 24
                        || next.getY() < 4 || next.getY() > 200) {
                    continue;
                }
                if (visited.add(key(next)) && handle.getType(next).getBlock().getMaterial()
                        == net.minecraft.server.v1_8_R3.Material.LAVA) {
                    queue.add(next);
                }
            }
        }
    }

    private static long key(BlockPosition pos) {
        return ((long) pos.getX() & 0x3FFFFFF) << 38 | ((long) pos.getZ() & 0x3FFFFFF) << 12
                | (pos.getY() & 0xFFF);
    }

    private long mix(long cx, long cz, long salt) {
        long h = this.seed ^ salt ^ (cx * 0x9E3779B97F4A7C15L) ^ (cz * 0xC2B2AE3D27D4EB4FL);
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        return h ^ (h >>> 31);
    }
}
