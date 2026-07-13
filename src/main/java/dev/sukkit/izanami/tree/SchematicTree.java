package dev.sukkit.izanami.tree;

import dev.sukkit.izanami.schem.Schematic;
import net.minecraft.server.v1_8_R3.BlockPosition;
import net.minecraft.server.v1_8_R3.World;

import java.util.Random;

/**
 * Arbre ou structure collé depuis un {@link Schematic}, ancré au sol.
 * <p>
 * La rotation aléatoire (0/90/180/270°) ne tourne que les coordonnées — les
 * blocs orientés (escaliers, vignes…) gardent leur data. Adaptée aux arbres
 * (troncs verticaux), à désactiver pour les structures directionnelles.
 */
public final class SchematicTree implements IzanamiTree {

    private final Schematic schematic;
    private final boolean randomRotation;

    public SchematicTree(Schematic schematic, boolean randomRotation) {
        if (schematic == null) {
            throw new IllegalArgumentException("schematic null");
        }
        this.schematic = schematic;
        this.randomRotation = randomRotation;
    }

    public SchematicTree(Schematic schematic) {
        this(schematic, true);
    }

    @Override
    public boolean place(World world, Random random, BlockPosition surface) {
        // le bloc sous la surface doit être plein (pas d'arbre sur l'eau ou une pente vide)
        if (!world.getType(surface.down()).getBlock().getMaterial().isBuildable()) {
            return false;
        }
        this.schematic.paste(world, surface, this.randomRotation ? random.nextInt(4) : 0);
        return true;
    }
}
