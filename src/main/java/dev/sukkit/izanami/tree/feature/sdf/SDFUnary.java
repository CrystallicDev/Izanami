package dev.sukkit.izanami.tree.feature.sdf;

import dev.sukkit.izanami.tree.placer.BlockPos;
import net.minecraft.server.v1_8_R3.IBlockData;

public abstract class SDFUnary extends SDF {
    protected SDF source;

    public SDFUnary setSource(SDF source) { this.source = source; return this; }

    @Override
    public IBlockData getBlockState(BlockPos pos) {
        return this.source.getBlockState(pos);
    }
}
