package dev.sukkit.izanami.tree.feature.sdf;

import dev.sukkit.izanami.tree.placer.BlockPos;
import dev.sukkit.izanami.tree.placer.Direction;
import net.minecraft.server.v1_8_R3.Blocks;
import net.minecraft.server.v1_8_R3.IBlockData;

import java.util.Map;

/** Info d'un bloc placé par un {@link SDF} (état + voisinage), triable par hauteur. */
public class PosInfo implements Comparable<PosInfo> {

    private static final IBlockData AIR = Blocks.AIR.getBlockData();

    private final Map<BlockPos, PosInfo> blocks;
    private final Map<BlockPos, PosInfo> add;
    private final BlockPos pos;
    private IBlockData state;

    public static PosInfo create(Map<BlockPos, PosInfo> blocks, Map<BlockPos, PosInfo> add, BlockPos pos) {
        return new PosInfo(blocks, add, pos);
    }

    private PosInfo(Map<BlockPos, PosInfo> blocks, Map<BlockPos, PosInfo> add, BlockPos pos) {
        this.blocks = blocks;
        this.add = add;
        this.pos = pos;
        blocks.put(pos, this);
    }

    public IBlockData getState() {
        return this.state;
    }

    public IBlockData getState(BlockPos pos) {
        PosInfo info = this.blocks.get(pos);
        if (info == null) {
            info = this.add.get(pos);
            return info == null ? AIR : info.getState();
        }
        return info.getState();
    }

    public void setState(IBlockData state) {
        this.state = state;
    }

    public void setState(BlockPos pos, IBlockData state) {
        PosInfo info = this.blocks.get(pos);
        if (info != null) {
            info.setState(state);
        }
    }

    public IBlockData getState(Direction dir) {
        PosInfo info = this.blocks.get(this.pos.relative(dir));
        if (info == null) {
            info = this.add.get(this.pos.relative(dir));
            return info == null ? AIR : info.getState();
        }
        return info.getState();
    }

    public IBlockData getState(Direction dir, int distance) {
        PosInfo info = this.blocks.get(this.pos.relative(dir, distance));
        return info == null ? AIR : info.getState();
    }

    public IBlockData getStateUp() {
        return getState(Direction.UP);
    }

    public IBlockData getStateDown() {
        return getState(Direction.DOWN);
    }

    public BlockPos getPos() {
        return this.pos;
    }

    public void setBlockPos(BlockPos pos, IBlockData state) {
        PosInfo info = new PosInfo(this.blocks, this.add, pos);
        info.state = state;
        this.add.put(pos, info);
    }

    @Override
    public int hashCode() {
        return this.pos.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof PosInfo && this.pos.equals(((PosInfo) obj).pos);
    }

    @Override
    public int compareTo(PosInfo info) {
        return this.pos.getY() - info.pos.getY();
    }
}
