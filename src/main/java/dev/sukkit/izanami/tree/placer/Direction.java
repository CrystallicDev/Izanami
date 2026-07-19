package dev.sukkit.izanami.tree.placer;

/**
 * Direction cardinale/verticale façon {@code net.minecraft.core.Direction},
 * pour porter les placers des versions supérieures.
 */
public enum Direction {

    DOWN(0, -1, 0),
    UP(0, 1, 0),
    NORTH(0, 0, -1),
    SOUTH(0, 0, 1),
    WEST(-1, 0, 0),
    EAST(1, 0, 0);

    private final int stepX;
    private final int stepY;
    private final int stepZ;

    Direction(int stepX, int stepY, int stepZ) {
        this.stepX = stepX;
        this.stepY = stepY;
        this.stepZ = stepZ;
    }

    public int getStepX() {
        return this.stepX;
    }

    public int getStepY() {
        return this.stepY;
    }

    public int getStepZ() {
        return this.stepZ;
    }

    public Direction getClockWise() {
        switch (this) {
            case NORTH: return EAST;
            case EAST: return SOUTH;
            case SOUTH: return WEST;
            case WEST: return NORTH;
            default: return this;
        }
    }

    public Direction getCounterClockWise() {
        switch (this) {
            case NORTH: return WEST;
            case WEST: return SOUTH;
            case SOUTH: return EAST;
            case EAST: return NORTH;
            default: return this;
        }
    }

    public Direction getOpposite() {
        switch (this) {
            case NORTH: return SOUTH;
            case SOUTH: return NORTH;
            case WEST: return EAST;
            case EAST: return WEST;
            case UP: return DOWN;
            default: return UP;
        }
    }
}
