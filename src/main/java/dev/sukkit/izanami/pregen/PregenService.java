package dev.sukkit.izanami.pregen;

import dev.sukkit.izanami.IzanamiPlugin;
import org.bukkit.World;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Gère les jobs de prégénération (un au plus par monde). Un seul job global à
 * la fois est autorisé : la génération se partage le main thread, deux jobs
 * simultanés se voleraient le budget sans aller plus vite.
 */
public final class PregenService {

    private final IzanamiPlugin plugin;
    private final PregenSettings settings;
    private final Map<String, PregenJob> jobs = new LinkedHashMap<>();

    public PregenService(IzanamiPlugin plugin) {
        this.plugin = plugin;
        this.settings = PregenSettings.fromConfig(plugin.getConfig());
    }

    /**
     * @param distanceBlocks demi-côté du carré en blocs (la zone -X..+X est couverte)
     * @throws IllegalStateException si un job tourne déjà
     */
    public PregenJob start(World world, int distanceBlocks, UUID senderId) {
        if (!this.jobs.isEmpty()) {
            throw new IllegalStateException("Une pregeneration est deja en cours ("
                    + this.jobs.keySet().iterator().next() + "). /izanami pregen stop d'abord.");
        }
        // +1 chunk de bordure pour que toute la zone demandée soit décorée
        // (la population 1.8 exige la présence des chunks voisins)
        int radiusChunks = (distanceBlocks + 15) / 16 + 1;
        PregenJob job = new PregenJob(this.plugin, this.settings, world, radiusChunks, senderId);
        this.jobs.put(key(world.getName()), job);
        job.start();
        return job;
    }

    public boolean stop(String worldName) {
        PregenJob job = this.jobs.remove(key(worldName));
        if (job == null) {
            return false;
        }
        job.cancel();
        return true;
    }

    public void stopAll() {
        for (PregenJob job : this.jobs.values()) {
            job.cancel();
        }
        this.jobs.clear();
    }

    /** Appelé par le job lui-même à sa complétion. */
    public void onJobFinished(String worldName) {
        this.jobs.remove(key(worldName));
    }

    public Collection<PregenJob> getJobs() {
        return Collections.unmodifiableCollection(this.jobs.values());
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
