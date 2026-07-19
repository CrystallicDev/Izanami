package dev.sukkit.izanami.tree.feature.sdf;

import dev.sukkit.izanami.tree.feature.BlocksHelper;
import dev.sukkit.izanami.tree.placer.BlockPos;
import dev.sukkit.izanami.tree.placer.Direction;
import net.minecraft.server.v1_8_R3.IBlockData;
import net.minecraft.server.v1_8_R3.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Champ de distance signé (SDF), porté depuis SALM/bclib (BetterEnd). Adapté à
 * la 1.8.9 : {@code BlockPos} immuable (dev.sukkit.izanami.tree.placer),
 * {@code IBlockData}, {@code World} NMS. {@code fillRecursive} propage depuis
 * l'origine tant que la distance est négative, applique les post-process (triés
 * par hauteur) puis pose les blocs. Garde {@code isLoaded} pour ne jamais forcer
 * un chargement de chunk pendant la décoration.
 */
public abstract class SDF {

    private final List<Function<PosInfo, IBlockData>> postProcesses = new ArrayList<>();
    private Function<IBlockData, Boolean> canReplace = state -> state.getBlock().getMaterial().isReplaceable();

    public abstract float getDistance(float x, float y, float z);

    public abstract IBlockData getBlockState(BlockPos pos);

    public SDF addPostProcess(Function<PosInfo, IBlockData> postProcess) {
        this.postProcesses.add(postProcess);
        return this;
    }

    public SDF setReplaceFunction(Function<IBlockData, Boolean> canReplace) {
        this.canReplace = canReplace;
        return this;
    }

    public void fillRecursive(World world, BlockPos start) {
        Map<BlockPos, PosInfo> mapWorld = new HashMap<>();
        Map<BlockPos, PosInfo> addInfo = new HashMap<>();
        Set<BlockPos> blocks = new HashSet<>();
        Set<BlockPos> ends = new HashSet<>();
        Set<BlockPos> add = new HashSet<>();
        ends.add(new BlockPos(0, 0, 0));
        boolean run = true;

        while (run) {
            for (BlockPos center : ends) {
                for (Direction dir : Direction.values()) {
                    BlockPos bPos = center.relative(dir);
                    BlockPos wpos = bPos.offset(start);
                    if (!blocks.contains(bPos) && world.isLoaded(wpos.toNms())
                            && canReplace.apply(world.getType(wpos.toNms()))) {
                        if (this.getDistance(bPos.getX(), bPos.getY(), bPos.getZ()) < 0) {
                            IBlockData state = getBlockState(wpos);
                            PosInfo.create(mapWorld, addInfo, wpos).setState(state);
                            add.add(bPos);
                        }
                    }
                }
            }

            blocks.addAll(ends);
            ends.clear();
            ends.addAll(add);
            add.clear();
            run &= !ends.isEmpty();
        }

        List<PosInfo> infos = new ArrayList<>(mapWorld.values());
        if (!infos.isEmpty()) {
            Collections.sort(infos);
            applyPostProcess(infos);
            for (PosInfo info : infos) {
                BlocksHelper.setBlockForFeatures(world, info.getPos(), info.getState());
            }

            infos.clear();
            infos.addAll(addInfo.values());
            Collections.sort(infos);
            applyPostProcess(infos);
            for (PosInfo info : infos) {
                if (world.isLoaded(info.getPos().toNms())
                        && canReplace.apply(world.getType(info.getPos().toNms()))) {
                    BlocksHelper.setBlockForFeatures(world, info.getPos(), info.getState());
                }
            }
        }
    }

    /**
     * Comme {@link #fillRecursive} mais traverse les blocs {@code ignore}
     * (ex. les rondins) sans les remplacer — utilisé pour poser du feuillage
     * autour d'un tronc existant.
     */
    public void fillRecursiveIgnore(World world, BlockPos start, Function<IBlockData, Boolean> ignore) {
        Map<BlockPos, PosInfo> mapWorld = new HashMap<>();
        Map<BlockPos, PosInfo> addInfo = new HashMap<>();
        Set<BlockPos> blocks = new HashSet<>();
        Set<BlockPos> ends = new HashSet<>();
        Set<BlockPos> add = new HashSet<>();
        ends.add(new BlockPos(0, 0, 0));
        boolean run = true;

        while (run) {
            for (BlockPos center : ends) {
                for (Direction dir : Direction.values()) {
                    BlockPos bPos = center.relative(dir);
                    BlockPos wpos = bPos.offset(start);
                    if (blocks.contains(bPos) || !world.isLoaded(wpos.toNms())) {
                        continue;
                    }
                    IBlockData state = world.getType(wpos.toNms());
                    boolean ign = ignore.apply(state);
                    if (ign || canReplace().apply(state)) {
                        if (this.getDistance(bPos.getX(), bPos.getY(), bPos.getZ()) < 0) {
                            PosInfo.create(mapWorld, addInfo, wpos).setState(ign ? state : getBlockState(wpos));
                            add.add(bPos);
                        }
                    }
                }
            }

            blocks.addAll(ends);
            ends.clear();
            ends.addAll(add);
            add.clear();
            run &= !ends.isEmpty();
        }

        List<PosInfo> infos = new ArrayList<>(mapWorld.values());
        if (!infos.isEmpty()) {
            Collections.sort(infos);
            applyPostProcess(infos);
            for (PosInfo info : infos) {
                BlocksHelper.setBlockForFeatures(world, info.getPos(), info.getState());
            }

            infos.clear();
            infos.addAll(addInfo.values());
            Collections.sort(infos);
            applyPostProcess(infos);
            for (PosInfo info : infos) {
                if (world.isLoaded(info.getPos().toNms())
                        && canReplace().apply(world.getType(info.getPos().toNms()))) {
                    BlocksHelper.setBlockForFeatures(world, info.getPos(), info.getState());
                }
            }
        }
    }

    private Function<IBlockData, Boolean> canReplace() {
        return this.canReplace;
    }

    private void applyPostProcess(List<PosInfo> infos) {
        for (Function<PosInfo, IBlockData> postProcess : this.postProcesses) {
            for (PosInfo info : infos) {
                info.setState(postProcess.apply(info));
            }
        }
    }
}
