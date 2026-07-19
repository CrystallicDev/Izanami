package dev.sukkit.izanami.gen;

import dev.sukkit.izanami.custom.CustomBlock;
import net.minecraft.server.v1_8_R3.Block;
import net.minecraft.server.v1_8_R3.BlockPosition;
import net.minecraft.server.v1_8_R3.Blocks;
import net.minecraft.server.v1_8_R3.IBlockData;
import net.minecraft.server.v1_8_R3.WorldServer;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_8_R3.CraftWorld;
import org.bukkit.generator.BlockPopulator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;

/**
 * Retire les états hôtes des blocs custom À MODÈLE que le décorateur vanilla
 * pose en surface (ex. oxeye daisy = hôte du spore blossom) : contrairement au
 * CTM, un swap de modèle via blockstates ne peut pas être filtré par hauteur
 * côté client, la géométrie custom apparaîtrait donc sur les fleurs de plaine.
 * <p>
 * Critère : un hôte {@code attach=CEILING} sans plafond solide (ou FLOOR sans
 * sol) est forcément une fleur vanilla mal tombée — nos décorations respectent
 * toujours leur mode d'attache. Remplacée par un coquelicot (hôte red_flower)
 * ou de l'air.
 */
public final class HostCleanupPopulator extends BlockPopulator {

    private final List<IBlockData> hosts = new ArrayList<>();
    private final List<CustomBlock.Attach> attaches = new ArrayList<>();

    public HostCleanupPopulator(Collection<CustomBlock> blocks) {
        for (CustomBlock block : blocks) {
            if (block.getModel() == null || block.getAttach() == CustomBlock.Attach.ANY) {
                continue; // CTM : déjà filtré par heights/biomes côté client
            }
            Block host = Block.getById(block.getBlockId());
            if (host != null) {
                this.hosts.add(host.fromLegacyData(block.getMeta()));
                this.attaches.add(block.getAttach());
            }
        }
    }

    public boolean isEmpty() {
        return this.hosts.isEmpty();
    }

    @Override
    public void populate(World world, Random random, Chunk chunk) {
        WorldServer handle = ((CraftWorld) world).getHandle();
        int baseX = chunk.getX() << 4;
        int baseZ = chunk.getZ() << 4;

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 8; y <= 160; y++) {
                    BlockPosition pos = new BlockPosition(baseX + x, y, baseZ + z);
                    IBlockData state = handle.getType(pos);
                    int index = this.hosts.indexOf(state);
                    if (index < 0) {
                        continue;
                    }
                    BlockPosition support = this.attaches.get(index) == CustomBlock.Attach.CEILING
                            ? pos.up() : pos.down();
                    if (handle.getType(support).getBlock().isOccluding()) {
                        continue; // attache valide : décoration Izanami légitime
                    }
                    handle.setTypeAndData(pos, state.getBlock() == Blocks.RED_FLOWER
                            ? Blocks.RED_FLOWER.fromLegacyData(0) : Blocks.AIR.getBlockData(), 2);
                }
            }
        }
    }
}
