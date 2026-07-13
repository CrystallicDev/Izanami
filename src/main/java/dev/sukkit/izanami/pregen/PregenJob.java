package dev.sukkit.izanami.pregen;

import dev.sukkit.izanami.IzanamiPlugin;
import net.minecraft.server.v1_8_R3.ChunkRegionLoader;
import net.minecraft.server.v1_8_R3.MinecraftServer;
import net.minecraft.server.v1_8_R3.WorldServer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_8_R3.CraftWorld;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Field;
import java.util.UUID;

/**
 * Job de prégénération d'un carré de chunks autour de (0,0), exécuté sur le
 * main thread (la génération 1.8 n'est pas thread-safe) avec un budget de
 * temps par tick adaptatif au TPS.
 * <p>
 * - Les chunks déjà présents sur disque sont sautés via
 *   {@link ChunkRegionLoader#chunkExists} (lecture d'en-tête de région, rapide)
 *   → relancer la commande après une interruption reprend là où on en était.<br>
 * - Les chunks générés sont immédiatement mis en file d'unload ; l'écriture
 *   disque est asynchrone (FileIOThread vanilla).<br>
 * - Backpressure : si trop de chunks sont chargés (file d'unload en retard),
 *   le job saute des ticks pour laisser le serveur drainer.
 */
public final class PregenJob {

    private final IzanamiPlugin plugin;
    private final PregenSettings settings;
    private final World world;
    private final WorldServer handle;
    private final ChunkRegionLoader loader; // null si loader inattendu -> pas de skip disque
    private final UUID senderId;            // null = console

    private final int radiusChunks;
    private final long totalChunks;

    // curseur row-major : de -radius à +radius sur les deux axes
    private int cursorX;
    private int cursorZ;
    private boolean done;

    private long processed;
    private long generated;
    private long skipped;

    private double budgetMillis;
    private final long startNanos;
    private long lastLogNanos;
    private long lastLogProcessed;

    private BukkitTask task;

    public PregenJob(IzanamiPlugin plugin, PregenSettings settings, World world, int radiusChunks, UUID senderId) {
        this.plugin = plugin;
        this.settings = settings;
        this.world = world;
        this.handle = ((CraftWorld) world).getHandle();
        this.loader = extractLoader(this.handle);
        this.senderId = senderId;
        this.radiusChunks = radiusChunks;
        long side = 2L * radiusChunks + 1L;
        this.totalChunks = side * side;
        this.cursorX = -radiusChunks;
        this.cursorZ = -radiusChunks;
        this.budgetMillis = settings.getMaxMillisPerTick();
        this.startNanos = System.nanoTime();
        this.lastLogNanos = this.startNanos;
    }

    /**
     * Le champ ChunkProviderServer.chunkLoader est private : lecture par
     * réflexion une seule fois au démarrage du job. En cas d'échec (fork
     * modifié), on dégrade proprement : plus de skip disque, la reprise
     * repasse par une génération complète (les chunks existants sont
     * simplement rechargés puis déchargés, pas régénérés).
     */
    private static ChunkRegionLoader extractLoader(WorldServer handle) {
        try {
            Field field = handle.chunkProviderServer.getClass().getDeclaredField("chunkLoader");
            field.setAccessible(true);
            Object value = field.get(handle.chunkProviderServer);
            return value instanceof ChunkRegionLoader ? (ChunkRegionLoader) value : null;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    public void start() {
        this.task = Bukkit.getScheduler().runTaskTimer(this.plugin, this::tick, 1L, 1L);
        log("Pregen '" + this.world.getName() + "' demarree : rayon " + this.radiusChunks
                + " chunks (" + this.totalChunks + " chunks, ~" + estimateMb() + " Mo)"
                + (this.loader != null ? ", reprise auto des chunks existants" : ""));
    }

    private void tick() {
        if (this.done) {
            return;
        }

        // Backpressure : laisser la file d'unload (100 chunks/tick) se vider.
        if (this.handle.chunkProviderServer.getLoadedChunks() > this.settings.getMaxLoadedChunks()) {
            maybeLog();
            return;
        }

        adjustBudget();
        long tickStart = System.nanoTime();
        long budgetNanos = (long) (this.budgetMillis * 1_000_000.0);

        int stepsThisTick = 0;
        while (!this.done) {
            // au moins un chunk par tick pour garantir la progression
            if (stepsThisTick > 0 && System.nanoTime() - tickStart >= budgetNanos) {
                break;
            }
            step();
            stepsThisTick++;
        }

        maybeLog();
        if (this.done) {
            finish();
        }
    }

    private void step() {
        int cx = this.cursorX;
        int cz = this.cursorZ;
        advance();

        if (this.loader != null && this.loader.chunkExists(this.handle, cx, cz)) {
            this.skipped++;
        } else {
            this.world.getChunkAt(cx, cz);
            this.world.unloadChunkRequest(cx, cz);
            this.generated++;
        }
        this.processed++;
    }

    private void advance() {
        if (this.cursorX < this.radiusChunks) {
            this.cursorX++;
            return;
        }
        this.cursorX = -this.radiusChunks;
        if (this.cursorZ < this.radiusChunks) {
            this.cursorZ++;
        } else {
            this.done = true;
        }
    }

    /** Contrôleur simple : réduction rapide sous le TPS cible, remontée lente au-dessus. */
    private void adjustBudget() {
        double tps = MinecraftServer.getServer().recentTps[0];
        if (tps < this.settings.getTargetTps()) {
            this.budgetMillis = Math.max(2.0, this.budgetMillis * 0.75);
        } else if (this.budgetMillis < this.settings.getMaxMillisPerTick()) {
            this.budgetMillis = Math.min(this.settings.getMaxMillisPerTick(), this.budgetMillis + 0.5);
        }
    }

    private void maybeLog() {
        long now = System.nanoTime();
        if (now - this.lastLogNanos < this.settings.getLogIntervalSeconds() * 1_000_000_000L) {
            return;
        }
        long processedSinceLog = this.processed - this.lastLogProcessed;
        double seconds = (now - this.lastLogNanos) / 1_000_000_000.0;
        long rate = (long) (processedSinceLog / seconds);
        long remaining = this.totalChunks - this.processed;
        String eta = rate > 0 ? formatDuration(remaining / Math.max(1, rate)) : "?";

        log(String.format("Pregen '%s' : %.1f%% (%d/%d chunks, %d/s, ETA %s, budget %.0f ms, %d charges)",
                this.world.getName(), this.processed * 100.0 / this.totalChunks,
                this.processed, this.totalChunks, rate, eta, this.budgetMillis,
                this.handle.chunkProviderServer.getLoadedChunks()));

        this.lastLogNanos = now;
        this.lastLogProcessed = this.processed;
    }

    private void finish() {
        cancel();
        this.world.save(); // flush du reste (l'ecriture chunk est deja async)
        long seconds = (System.nanoTime() - this.startNanos) / 1_000_000_000L;
        log("Pregen '" + this.world.getName() + "' terminee en " + formatDuration(seconds)
                + " : " + this.generated + " chunks generes, " + this.skipped + " deja presents.");
        this.plugin.getPregenService().onJobFinished(this.world.getName());
    }

    /** Arrêt manuel (commande ou onDisable). La reprise se fait en relançant la commande. */
    public void cancel() {
        if (this.task != null) {
            this.task.cancel();
            this.task = null;
        }
        this.done = true;
    }

    public String statusLine() {
        return String.format("%s : %.1f%% (%d/%d chunks, rayon %d)",
                this.world.getName(), this.processed * 100.0 / this.totalChunks,
                this.processed, this.totalChunks, this.radiusChunks);
    }

    private void log(String message) {
        this.plugin.getLogger().info(message);
        if (this.senderId != null) {
            Player player = Bukkit.getPlayer(this.senderId);
            if (player != null) {
                player.sendMessage(ChatColor.GRAY + "[Izanami] " + message);
            }
        }
    }

    private long estimateMb() {
        return this.totalChunks * 10L / 1024L; // ~10 Ko par chunk en moyenne
    }

    private static String formatDuration(long seconds) {
        if (seconds >= 3600) {
            return seconds / 3600 + "h" + (seconds % 3600) / 60 + "m";
        }
        if (seconds >= 60) {
            return seconds / 60 + "m" + seconds % 60 + "s";
        }
        return seconds + "s";
    }
}
