package dev.sukkit.izanami.tree;

import net.minecraft.server.v1_8_R3.BlockPosition;
import net.minecraft.server.v1_8_R3.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Choisit au hasard une variante d'arbre à chaque placement : une seule entrée
 * de {@link IzanamiForest} donne ainsi de la variété sans multiplier les
 * grilles (donc sans arbres superposés).
 */
public final class RandomTree implements IzanamiTree {

    private final List<IzanamiTree> variants;

    public RandomTree(List<IzanamiTree> variants) {
        if (variants == null || variants.isEmpty()) {
            throw new IllegalArgumentException("aucune variante d'arbre");
        }
        this.variants = new ArrayList<>(variants);
    }

    @Override
    public boolean place(World world, Random random, BlockPosition surface) {
        return this.variants.get(random.nextInt(this.variants.size())).place(world, random, surface);
    }
}
