package dev.sukkit.izanami.api;

import dev.sukkit.izanami.tree.IzanamiForest;
import net.minecraft.server.v1_8_R3.BiomeBase;
import net.minecraft.server.v1_8_R3.BiomeDecorator;
import net.minecraft.server.v1_8_R3.BlockPosition;
import net.minecraft.server.v1_8_R3.IBlockData;
import net.minecraft.server.v1_8_R3.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Biome de surface custom. S'enregistre dans le registre NMS (IDs libres
 * 40–127), donc utilisable comme un biome vanilla (whitelist, biome central,
 * décoration, mobs). Côté client, l'ID est remappé vers {@link #getClientBiome()}
 * (patch Sukkit BiomeClientRemap). Construction via {@link Builder}.
 * À enregistrer à l'enable du plugin, avant la création des mondes.
 */
public class CustomBiome extends BiomeBase {

    private final BiomeBase clientBiome;

    protected CustomBiome(int id, Builder builder) {
        super(id);
        this.a(builder.name);
        this.b(builder.color);
        this.temperature = builder.temperature;
        this.humidity = builder.humidity;
        this.an = builder.heightBase;
        this.ao = builder.heightVariation;
        if (builder.topBlock != null) {
            this.ak = builder.topBlock;
        }
        if (builder.fillerBlock != null) {
            this.al = builder.fillerBlock;
        }
        if (!builder.rain) {
            this.ay = false;
        }
        this.clientBiome = builder.clientBiome;
        this.as = new CustomBiomeDecorator(builder);

        if (builder.clearDefaultMobs) {
            this.at.clear(); // monstres
            this.au.clear(); // creatures
            this.av.clear(); // aquatiques
            this.aw.clear(); // ambiants (chauve-souris)
        }
        this.at.addAll(builder.monsters);
        this.au.addAll(builder.creatures);
    }

    /** Biome vanilla dont le client affichera les couleurs/la météo. */
    public BiomeBase getClientBiome() {
        return this.clientBiome;
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public static final class Builder {

        final String name;
        int requestedId = -1;
        BiomeBase clientBiome = BiomeBase.PLAINS;
        float temperature = 0.5F;
        float humidity = 0.5F;
        float heightBase = 0.1F;
        float heightVariation = 0.2F;
        int color = 9286496;
        IBlockData topBlock;
        IBlockData fillerBlock;
        boolean rain = true;
        boolean clearDefaultMobs = false;
        IzanamiForest forest;
        final List<BiomeMeta> monsters = new ArrayList<>();
        final List<BiomeMeta> creatures = new ArrayList<>();

        // compteurs de decoration par chunk
        int trees = 0;
        int flowers = 2;
        int grass = 1;
        int deadBush = 0;
        int mushrooms = 0;
        int reeds = 0;
        int cacti = 0;
        int waterlily = 0;
        int bigMushrooms = 0;

        Builder(String name) {
            if (name == null || name.trim().isEmpty()) {
                throw new IllegalArgumentException("Nom de biome vide");
            }
            this.name = name.trim();
        }

        /** ID explicite (40–127) ; sinon premier ID libre. */
        public Builder id(int id) { this.requestedId = id; return this; }

        /** Biome vanilla affiché par le client (couleurs, pluie/neige). */
        public Builder clientBiome(BiomeBase biome) {
            if (biome == null) throw new IllegalArgumentException("clientBiome null");
            this.clientBiome = biome;
            return this;
        }

        public Builder temperature(float temperature) { this.temperature = temperature; return this; }
        public Builder humidity(float humidity) { this.humidity = humidity; return this; }

        /** Hauteur du terrain : base (ex. 0.125 plaines) et variation (ex. 1.8 montagnes). */
        public Builder height(float base, float variation) {
            this.heightBase = base;
            this.heightVariation = variation;
            return this;
        }

        public Builder color(int color) { this.color = color; return this; }

        /** Bloc de surface (défaut : herbe). */
        public Builder topBlock(IBlockData block) { this.topBlock = block; return this; }

        /** Bloc de sous-surface (défaut : terre). */
        public Builder fillerBlock(IBlockData block) { this.fillerBlock = block; return this; }

        public Builder noRain() { this.rain = false; return this; }

        public Builder trees(int perChunk) { this.trees = perChunk; return this; }
        public Builder flowers(int perChunk) { this.flowers = perChunk; return this; }
        public Builder grass(int perChunk) { this.grass = perChunk; return this; }
        public Builder deadBush(int perChunk) { this.deadBush = perChunk; return this; }
        public Builder mushrooms(int perChunk) { this.mushrooms = perChunk; return this; }
        public Builder reeds(int perChunk) { this.reeds = perChunk; return this; }
        public Builder cacti(int perChunk) { this.cacti = perChunk; return this; }
        public Builder waterlily(int perChunk) { this.waterlily = perChunk; return this; }
        public Builder bigMushrooms(int perChunk) { this.bigMushrooms = perChunk; return this; }

        /**
         * Forêt Izanami (arbres Java/schematic, espacements par type) placée
         * pendant la décoration, en plus des compteurs vanilla ci-dessus.
         */
        public Builder forest(IzanamiForest forest) { this.forest = forest; return this; }

        /** Vide les listes de spawn par défaut (mobs vanilla). */
        public Builder clearMobs() { this.clearDefaultMobs = true; return this; }

        public Builder addMonster(Class<? extends net.minecraft.server.v1_8_R3.EntityInsentient> entityClass,
                                  int weight, int minGroup, int maxGroup) {
            this.monsters.add(new BiomeMeta(entityClass, weight, minGroup, maxGroup));
            return this;
        }

        public Builder addCreature(Class<? extends net.minecraft.server.v1_8_R3.EntityInsentient> entityClass,
                                   int weight, int minGroup, int maxGroup) {
            this.creatures.add(new BiomeMeta(entityClass, weight, minGroup, maxGroup));
            return this;
        }

        /** Enregistre le biome (registre NMS + remap client Sukkit). */
        public CustomBiome register() {
            return IzanamiBiomeRegistry.register(this);
        }
    }

    /**
     * BiomeDecorator paramétré : sous-classe pour accéder aux compteurs
     * protégés depuis un autre package, et pour brancher la forêt Izanami
     * après la décoration vanilla.
     */
    private static final class CustomBiomeDecorator extends BiomeDecorator {
        private final IzanamiForest forest;

        CustomBiomeDecorator(Builder builder) {
            this.forest = builder.forest;
            this.z = builder.waterlily;
            this.A = builder.trees <= 0 ? -999 : builder.trees; // convention vanilla : -999 = aucun
            this.B = builder.flowers;
            this.C = builder.grass;
            this.D = builder.deadBush;
            this.E = builder.mushrooms;
            this.F = builder.reeds;
            this.G = builder.cacti;
            this.J = builder.bigMushrooms;
        }

        @Override
        public void a(World world, Random random, BiomeBase biome, BlockPosition position) {
            super.a(world, random, biome, position);
            if (this.forest != null) {
                this.forest.decorate(world, random, position, biome);
            }
        }
    }
}
