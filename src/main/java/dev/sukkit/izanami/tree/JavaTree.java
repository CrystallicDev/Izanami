package dev.sukkit.izanami.tree;

import net.minecraft.server.v1_8_R3.BlockPosition;
import net.minecraft.server.v1_8_R3.World;
import net.minecraft.server.v1_8_R3.WorldGenAcaciaTree;
import net.minecraft.server.v1_8_R3.WorldGenBigTree;
import net.minecraft.server.v1_8_R3.WorldGenForest;
import net.minecraft.server.v1_8_R3.WorldGenTaiga2;
import net.minecraft.server.v1_8_R3.WorldGenTrees;
import net.minecraft.server.v1_8_R3.WorldGenerator;

import java.util.Random;

/**
 * Arbre défini en Java : soit un générateur vanilla (fabriques statiques),
 * soit n'importe quel {@link WorldGenerator} custom.
 * <p>
 * Note : les générateurs d'arbres vanilla exigent de l'herbe ou de la terre
 * sous l'arbre — sur un sol custom (netherrack…), utiliser un SchematicTree
 * ou un WorldGenerator maison.
 */
public final class JavaTree implements IzanamiTree {

    private final WorldGenerator generator;

    public JavaTree(WorldGenerator generator) {
        if (generator == null) {
            throw new IllegalArgumentException("generator null");
        }
        this.generator = generator;
    }

    public static JavaTree oak() {
        return new JavaTree(new WorldGenTrees(false));
    }

    public static JavaTree bigOak() {
        return new JavaTree(new WorldGenBigTree(false));
    }

    public static JavaTree birch() {
        return new JavaTree(new WorldGenForest(false, false));
    }

    public static JavaTree spruce() {
        return new JavaTree(new WorldGenTaiga2(false));
    }

    public static JavaTree acacia() {
        return new JavaTree(new WorldGenAcaciaTree(false));
    }

    @Override
    public boolean place(World world, Random random, BlockPosition surface) {
        return this.generator.generate(world, random, surface);
    }
}
