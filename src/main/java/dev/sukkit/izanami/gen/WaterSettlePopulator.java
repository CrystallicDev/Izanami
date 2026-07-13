package dev.sukkit.izanami.gen;

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
 * "Réveille" l'eau générée pour qu'elle s'écoule dès la génération, au lieu de
 * rester statique jusqu'à un update manuel. L'eau posée est une source
 * stationnaire (flag 2) qui ne coule que sur {@code doPhysics} ; on l'appelle
 * donc sur l'eau au contact de l'air et le moteur de fluide fait le reste.
 * <p>
 * Souterrain ({@code [6, base-height-5]}) : contact d'air dans toute direction.
 * Surface ({@code ]base-height-5, base-height]}) : uniquement air directement en
 * dessous — une mer/un lac au-dessus d'un puits s'y déverse, mais pas les côtes.
 */
public final class WaterSettlePopulator extends BlockPopulator {

    private final int deepMaxY; // bande souterraine : écoulement dans toutes les directions
    private final int maxY;     // bande de surface : drainage par le bas uniquement

    public WaterSettlePopulator(int baseHeight) {
        this.deepMaxY = baseHeight - 5;
        this.maxY = baseHeight;
    }

    @Override
    public void populate(World world, Random random, Chunk chunk) {
        WorldServer handle = ((CraftWorld) world).getHandle();
        int baseX = chunk.getX() << 4;
        int baseZ = chunk.getZ() << 4;

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 6; y <= this.maxY; y++) {
                    BlockPosition pos = new BlockPosition(baseX + x, y, baseZ + z);
                    IBlockData state = handle.getType(pos);
                    if (state.getBlock() != Blocks.WATER) {
                        continue; // uniquement l'eau source stationnaire
                    }
                    boolean wake = y <= this.deepMaxY
                            ? touchesAir(handle, pos)     // souterrain : toutes directions
                            : isAir(handle, pos.down());  // surface : drainage par le bas
                    if (!wake) {
                        continue;
                    }
                    // simule un update de voisin sur la source : si elle peut
                    // couler, doPhysics la convertit en eau courante + planifie un tick
                    Blocks.WATER.doPhysics(handle, pos, state, Blocks.WATER);
                }
            }
        }
    }

    private static boolean touchesAir(WorldServer handle, BlockPosition pos) {
        return isAir(handle, pos.down()) || isAir(handle, pos.north()) || isAir(handle, pos.south())
                || isAir(handle, pos.east()) || isAir(handle, pos.west());
    }

    private static boolean isAir(WorldServer handle, BlockPosition pos) {
        return handle.getType(pos).getBlock() == Blocks.AIR;
    }
}
