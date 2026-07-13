package dev.sukkit.izanami.schem;

import net.minecraft.server.v1_8_R3.Block;
import net.minecraft.server.v1_8_R3.BlockPosition;
import net.minecraft.server.v1_8_R3.IBlockData;
import net.minecraft.server.v1_8_R3.NBTCompressedStreamTools;
import net.minecraft.server.v1_8_R3.NBTTagCompound;
import net.minecraft.server.v1_8_R3.World;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Schematic au format MCEdit (.schematic, NBT gzippé — produit par WorldEdit 6
 * et tous les outils 1.8) : Width/Height/Length, Blocks (+AddBlocks pour les
 * IDs &gt; 255), Data.
 * <p>
 * L'ancre de collage est le point (WEOffset si présent, sinon le centre du
 * plan XZ au niveau Y=0) : pour un arbre, schématiser avec le pied du tronc
 * comme origine WorldEdit donne un collage naturel sur le sol.
 * <p>
 * Immuable après chargement — thread-safe en lecture. Le collage lui-même doit
 * se faire sur le main thread (écritures monde).
 */
public final class Schematic {

    private final String name;
    private final int width;
    private final int height;
    private final int length;
    private final short[] blockIds;
    private final byte[] blockData;
    private final int offsetX;
    private final int offsetY;
    private final int offsetZ;

    private Schematic(String name, int width, int height, int length,
                      short[] blockIds, byte[] blockData, int offsetX, int offsetY, int offsetZ) {
        this.name = name;
        this.width = width;
        this.height = height;
        this.length = length;
        this.blockIds = blockIds;
        this.blockData = blockData;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.offsetZ = offsetZ;
    }

    public static Schematic load(File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            NBTTagCompound tag = NBTCompressedStreamTools.a(in);

            int width = tag.getShort("Width");
            int height = tag.getShort("Height");
            int length = tag.getShort("Length");
            byte[] rawBlocks = tag.getByteArray("Blocks");
            byte[] data = tag.getByteArray("Data");
            if (width <= 0 || height <= 0 || length <= 0 || rawBlocks.length != width * height * length) {
                throw new IOException("Schematic invalide : dimensions " + width + "x" + height + "x" + length
                        + ", " + rawBlocks.length + " blocs");
            }

            // AddBlocks : nibbles hauts pour les IDs > 255 (format MCEdit étendu)
            byte[] addBlocks = tag.hasKey("AddBlocks") ? tag.getByteArray("AddBlocks") : null;
            short[] blocks = new short[rawBlocks.length];
            for (int i = 0; i < rawBlocks.length; i++) {
                int id = rawBlocks[i] & 0xFF;
                if (addBlocks != null && (i >> 1) < addBlocks.length) {
                    int add = (i & 1) == 0 ? (addBlocks[i >> 1] & 0x0F) : ((addBlocks[i >> 1] >> 4) & 0x0F);
                    id |= add << 8;
                }
                blocks[i] = (short) id;
            }

            int offX = tag.hasKey("WEOffsetX") ? tag.getInt("WEOffsetX") : -(width / 2);
            int offY = tag.hasKey("WEOffsetY") ? tag.getInt("WEOffsetY") : 0;
            int offZ = tag.hasKey("WEOffsetZ") ? tag.getInt("WEOffsetZ") : -(length / 2);

            String name = file.getName().toLowerCase(java.util.Locale.ROOT).replace(".schematic", "");
            return new Schematic(name, width, height, length, blocks, data, offX, offY, offZ);
        }
    }

    /**
     * Colle le schematic, ancre à {@code anchor}. Les blocs d'air du schematic
     * sont ignorés (le terrain existant est préservé). Écritures avec flag 2
     * (envoi client, pas d'updates physiques — même convention que les arbres
     * vanilla).
     *
     * @param rotation 0, 1, 2 ou 3 quarts de tour (coordonnées uniquement — les
     *                 blocs orientés type escaliers ne sont pas re-mappés)
     */
    public void paste(World world, BlockPosition anchor, int rotation) {
        int index = 0;
        for (int y = 0; y < this.height; y++) {
            for (int z = 0; z < this.length; z++) {
                for (int x = 0; x < this.width; x++, index++) {
                    int id = this.blockIds[index];
                    if (id == 0) {
                        continue; // air : on préserve le terrain
                    }
                    Block block = Block.getById(id);
                    if (block == null) {
                        continue;
                    }
                    IBlockData blockData = block.fromLegacyData(this.blockData[index] & 15);

                    int rx = x + this.offsetX;
                    int rz = z + this.offsetZ;
                    for (int r = 0; r < (rotation & 3); r++) {
                        int tmp = rx;
                        rx = -rz;
                        rz = tmp;
                    }

                    world.setTypeAndData(new BlockPosition(
                            anchor.getX() + rx,
                            anchor.getY() + y + this.offsetY,
                            anchor.getZ() + rz), blockData, 2);
                }
            }
        }
    }

    public String getName() {
        return this.name;
    }

    public int getWidth() {
        return this.width;
    }

    public int getHeight() {
        return this.height;
    }

    public int getLength() {
        return this.length;
    }
}
