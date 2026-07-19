package dev.sukkit.izanami.tree.feature.sdf;

import dev.sukkit.izanami.tree.placer.BlockPos;
import net.minecraft.server.v1_8_R3.Block;
import net.minecraft.server.v1_8_R3.IBlockData;

import java.util.function.Function;

public abstract class SDFPrimitive extends SDF {
    protected Function<BlockPos, IBlockData> placerFunction;

    public SDFPrimitive setBlock(Function<BlockPos, IBlockData> placerFunction) {
        this.placerFunction = placerFunction;
        return this;
    }

    public SDFPrimitive setBlock(IBlockData state) {
        this.placerFunction = pos -> state;
        return this;
    }

    public SDFPrimitive setBlock(Block block) {
        this.placerFunction = pos -> block.getBlockData();
        return this;
    }

    @Override
    public IBlockData getBlockState(BlockPos pos) {
        return this.placerFunction.apply(pos);
    }
}
