package dev.sukkit.izanami.tree;

import net.minecraft.server.v1_8_R3.BlockPosition;
import net.minecraft.server.v1_8_R3.World;

import java.util.Random;

/**
 * Un "arbre" plaçable par Izanami — arbre vanilla ({@link JavaTree}), arbre ou
 * structure schematic ({@link SchematicTree}), ou implémentation Java libre.
 * <p>
 * Utilisé par {@link IzanamiForest} pendant la décoration des chunks, et
 * réutilisable directement par les plugins.
 */
public interface IzanamiTree {

    /**
     * Tente de placer l'arbre.
     *
     * @param surface position rendue par {@code World#getHighestBlockYAt} :
     *                le premier bloc au-dessus du sol
     * @return true si l'arbre a été placé
     */
    boolean place(World world, Random random, BlockPosition surface);
}
