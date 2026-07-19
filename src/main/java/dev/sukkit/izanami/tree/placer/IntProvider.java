package dev.sukkit.izanami.tree.placer;

import java.util.Random;

/**
 * Équivalent minimal de {@code net.minecraft.util.valueproviders.IntProvider}
 * (constante ou uniforme) pour les paramètres radius/offset des FoliagePlacer.
 */
public interface IntProvider {

    int sample(Random random);

    static IntProvider constant(int value) {
        return random -> value;
    }

    static IntProvider uniform(int min, int max) {
        return random -> max <= min ? min : min + random.nextInt(max - min + 1);
    }
}
