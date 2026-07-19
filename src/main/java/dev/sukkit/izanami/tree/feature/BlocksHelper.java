package dev.sukkit.izanami.tree.feature;

import dev.sukkit.izanami.tree.placer.BlockPos;
import net.minecraft.server.v1_8_R3.Block;
import net.minecraft.server.v1_8_R3.BlockLeaves;
import net.minecraft.server.v1_8_R3.BlockPosition;
import net.minecraft.server.v1_8_R3.Blocks;
import net.minecraft.server.v1_8_R3.IBlockData;
import net.minecraft.server.v1_8_R3.Material;
import net.minecraft.server.v1_8_R3.World;

/** Helpers de pose de bloc pour les features SDF (porté de SALM/bclib, adapté 1.8). */
public final class BlocksHelper {

    private BlocksHelper() {}

    /** Pose en flag 2 (envoi client, pas de physique) — convention worldgen. */
    public static void setBlockForFeatures(World world, BlockPos pos, IBlockData state) {
        BlockPosition p = pos.toNms();
        if (world.isLoaded(p)) {
            world.setTypeAndData(p, state, 2);
        }
    }

    public static void setWithoutUpdate(World world, BlockPos pos, IBlockData state) {
        setBlockForFeatures(world, pos, state);
    }

    /** Un bloc qu'un arbre peut traverser/remplacer : air, feuilles, ou végétation remplaçable. */
    public static Boolean replaceableOrPlant(IBlockData state) {
        Block block = state.getBlock();
        if (block == Blocks.AIR || block instanceof BlockLeaves) {
            return true;
        }
        Material material = block.getMaterial();
        return material.isReplaceable() || material == Material.PLANT;
    }
}
