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
    // Ancre "arbre" auto-détectée depuis les blocs (indépendante du WEOffset,
    // souvent absent ou incohérent) : base du tronc (rondin le plus bas) et
    // colonne du tronc, pour un collage toujours plaqué au sol.
    private final int trunkBaseY;
    private final int trunkX;
    private final int trunkZ;

    private Schematic(String name, int width, int height, int length,
                      short[] blockIds, byte[] blockData, int offsetX, int offsetY, int offsetZ,
                      int trunkBaseY, int trunkX, int trunkZ) {
        this.name = name;
        this.width = width;
        this.height = height;
        this.length = length;
        this.blockIds = blockIds;
        this.blockData = blockData;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.offsetZ = offsetZ;
        this.trunkBaseY = trunkBaseY;
        this.trunkX = trunkX;
        this.trunkZ = trunkZ;
    }

    /**
     * Ancre d'arbre {trunkBaseY, trunkX, trunkZ} = point qui sera plaqué au sol.
     * <ul>
     * <li>Si un WEOffset non nul est présent, c'est la position joueur de la
     *     sauvegarde (anchor voulu) : origine locale = -WEOffset.</li>
     * <li>Sinon (WEOffset absent ou (0,0,0)), détection auto de la ligne de sol :
     *     première couche « dense » (fin des racines éparses) et centroïde des
     *     rondins de cette couche.</li>
     * </ul>
     */
    private static int[] computeAnchor(short[] ids, int width, int height, int length,
                                       boolean weoSet, int weoX, int weoY, int weoZ) {
        if (weoSet) {
            return new int[]{-weoY, -weoX, -weoZ};
        }

        // logs par couche
        int[] perLayer = new int[height];
        int maxLower = 0;
        int lowerCap = Math.max(1, height / 3);
        for (int y = 0; y < height; y++) {
            int c = 0;
            for (int z = 0; z < length; z++) {
                for (int x = 0; x < width; x++) {
                    int id = ids[(y * length + z) * width + x];
                    if (id == 17 || id == 162) {
                        c++;
                    }
                }
            }
            perLayer[y] = c;
            if (y < lowerCap) {
                maxLower = Math.max(maxLower, c);
            }
        }
        // ligne de sol = 1re couche franchissant le seuil (racines éparses <, tronc >=)
        int threshold = Math.max(8, maxLower / 3);
        int baseY = -1;
        for (int y = 0; y < height; y++) {
            if (perLayer[y] >= threshold) {
                baseY = y;
                break;
            }
        }
        if (baseY < 0) {
            for (int y = 0; y < height; y++) {
                if (perLayer[y] > 0) {
                    baseY = y;
                    break;
                }
            }
        }
        if (baseY < 0) {
            return new int[]{0, width / 2, length / 2};
        }
        // centroïde des rondins de la couche de sol
        long sx = 0;
        long sz = 0;
        int n = 0;
        for (int z = 0; z < length; z++) {
            for (int x = 0; x < width; x++) {
                int id = ids[(baseY * length + z) * width + x];
                if (id == 17 || id == 162) {
                    sx += x;
                    sz += z;
                    n++;
                }
            }
        }
        int tx = n > 0 ? Math.round((float) sx / n) : width / 2;
        int tz = n > 0 ? Math.round((float) sz / n) : length / 2;
        return new int[]{baseY, tx, tz};
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

            int weoX = tag.hasKey("WEOffsetX") ? tag.getInt("WEOffsetX") : 0;
            int weoY = tag.hasKey("WEOffsetY") ? tag.getInt("WEOffsetY") : 0;
            int weoZ = tag.hasKey("WEOffsetZ") ? tag.getInt("WEOffsetZ") : 0;
            boolean weoSet = tag.hasKey("WEOffsetX") && !(weoX == 0 && weoY == 0 && weoZ == 0);

            // offset du collage générique (paste) : WEOffset si présent, sinon centrage XZ
            int offX = tag.hasKey("WEOffsetX") ? weoX : -(width / 2);
            int offY = tag.hasKey("WEOffsetY") ? weoY : 0;
            int offZ = tag.hasKey("WEOffsetZ") ? weoZ : -(length / 2);

            int[] anchor = computeAnchor(blocks, width, height, length, weoSet, weoX, weoY, weoZ);

            String name = file.getName().toLowerCase(java.util.Locale.ROOT).replace(".schematic", "");
            return new Schematic(name, width, height, length, blocks, data, offX, offY, offZ,
                    anchor[0], anchor[1], anchor[2]);
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

    /**
     * Colle un ARBRE en calant la base du tronc sur {@code ground} : la colonne
     * du tronc tombe sur (ground.x, ground.z) et le rondin le plus bas au niveau
     * de {@code ground.y} (les racines/terre sous le tronc passent sous le sol).
     * Ancre auto-détectée depuis les blocs — le WEOffset (Y) est ignoré, il est
     * souvent absent ou incohérent selon l'outil de sauvegarde.
     */
    public void pasteTree(World world, BlockPosition ground, int rotation) {
        int index = 0;
        for (int y = 0; y < this.height; y++) {
            for (int z = 0; z < this.length; z++) {
                for (int x = 0; x < this.width; x++, index++) {
                    int id = this.blockIds[index];
                    if (id == 0 || id == 20) {
                        continue; // air, et verre = marqueur d'ancre (les arbres n'en ont pas)
                    }
                    Block block = Block.getById(id);
                    if (block == null) {
                        continue;
                    }
                    IBlockData blockData = block.fromLegacyData(this.blockData[index] & 15);

                    int rx = x - this.trunkX;
                    int rz = z - this.trunkZ;
                    for (int r = 0; r < (rotation & 3); r++) {
                        int tmp = rx;
                        rx = -rz;
                        rz = tmp;
                    }
                    world.setTypeAndData(new BlockPosition(
                            ground.getX() + rx,
                            ground.getY() + y - this.trunkBaseY,
                            ground.getZ() + rz), blockData, 2);
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
