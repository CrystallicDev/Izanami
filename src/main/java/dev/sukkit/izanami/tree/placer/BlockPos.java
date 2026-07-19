package dev.sukkit.izanami.tree.placer;

import net.minecraft.server.v1_8_R3.BlockPosition;

/**
 * Position immuable façon 1.16+ ({@code net.minecraft.core.BlockPos}), pour
 * porter les TrunkPlacer/FoliagePlacer des versions supérieures avec un minimum
 * de changements. Converti en {@link BlockPosition} NMS au moment de l'écriture.
 */
public final class BlockPos {

    private final int x;
    private final int y;
    private final int z;

    public BlockPos(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public int getX() {
        return this.x;
    }

    public int getY() {
        return this.y;
    }

    public int getZ() {
        return this.z;
    }

    public BlockPos offset(int dx, int dy, int dz) {
        return new BlockPos(this.x + dx, this.y + dy, this.z + dz);
    }

    public BlockPos above() {
        return offset(0, 1, 0);
    }

    public BlockPos above(int n) {
        return offset(0, n, 0);
    }

    public BlockPos below() {
        return offset(0, -1, 0);
    }

    public BlockPos below(int n) {
        return offset(0, -n, 0);
    }

    public BlockPos relative(Direction dir) {
        return offset(dir.getStepX(), dir.getStepY(), dir.getStepZ());
    }

    public BlockPos relative(Direction dir, int n) {
        return offset(dir.getStepX() * n, dir.getStepY() * n, dir.getStepZ() * n);
    }

    public BlockPos offset(BlockPos o) {
        return offset(o.x, o.y, o.z);
    }

    /** Déjà immuable — présent pour compatibilité avec le code SDF porté. */
    public BlockPos immutable() {
        return this;
    }

    public BlockPosition toNms() {
        return new BlockPosition(this.x, this.y, this.z);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof BlockPos)) {
            return false;
        }
        BlockPos o = (BlockPos) obj;
        return this.x == o.x && this.y == o.y && this.z == o.z;
    }

    @Override
    public int hashCode() {
        return (this.x * 31 + this.y) * 31 + this.z;
    }
}
