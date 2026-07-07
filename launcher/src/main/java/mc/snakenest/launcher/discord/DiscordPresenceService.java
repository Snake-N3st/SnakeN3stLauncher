package mc.snakenest.launcher.discord;

import com.jagrosh.discordipc.IPCClient;
import com.jagrosh.discordipc.entities.RichPresence;
import com.jagrosh.discordipc.entities.pipe.PipeStatus;
import mc.snakenest.launcher.util.Log;

import java.time.OffsetDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * The only class in the launcher allowed to import {@code com.jagrosh.discordipc.*}.
 * Entirely best-effort and optional: a launcher client with no
 * {@code discord_app_id} configured (see {@code auth.ClientInfo}), or a
 * player who disabled it in Settings, simply never gets an instance of
 * this at all, and every method here swallows {@link Throwable} (not just
 * {@link Exception}) - the underlying library pulls in native code
 * (junixsocket, for the Unix domain socket used to reach Discord on
 * Linux/macOS) that could fail to load on some platform with an
 * {@link Error}, not an {@link Exception}, and Discord Rich Presence must
 * never be able to take down or block the rest of the launcher over that.
 *
 * <p>Does real (blocking) I/O in {@link #connect()} - call off the EDT.
 */
public final class DiscordPresenceService {

    /**
     * How often to retry after a failed/dropped connection. A single one-shot attempt at
     * launcher startup (the original implementation) meant a Discord client that wasn't fully
     * started yet at that exact moment - or one that starts/restarts later in the session -
     * never got Rich Presence back for the rest of the session; this is the actual fix for
     * "Discord status doesn't work" in the common case where it's just a timing/availability
     * issue rather than a real failure.
     */
    private static final long RECONNECT_INTERVAL_SECONDS = 15;

    /**
     * {@code IPCClient#connect()} performs a blocking handshake read with no timeout of its own.
     * If Discord ever accepts the raw socket connection but is slow/unresponsive answering the
     * handshake, that call can hang indefinitely - and since it used to run directly on {@link
     * #scheduler}'s single thread, a single hung attempt would silently kill every future retry
     * for the rest of the session (nothing left to run {@link #reconnectIfNeeded}). Each attempt
     * now runs on {@link #connectWorker} instead, bounded by this timeout - a stuck attempt is
     * simply abandoned (its thread may linger, but it's a daemon thread and this is an edge
     * case, not the common path) rather than wedging the whole retry loop.
     */
    private static final long CONNECT_TIMEOUT_SECONDS = 5;

    private final long clientId;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "discord-presence-reconnect");
        t.setDaemon(true);
        return t;
    });
    private final ExecutorService connectWorker = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "discord-presence-connect");
        t.setDaemon(true);
        return t;
    });
    private volatile IPCClient client;
    // Whatever setBrowsing()/setPlaying() was last asked to show, re-applied the moment a
    // (re)connect succeeds - without this, an activity requested while Discord wasn't reachable
    // yet would just be dropped instead of appearing once the retry loop finally connects.
    private volatile Runnable pendingActivity;

    private DiscordPresenceService(long clientId) {
        this.clientId = clientId;
    }

    /** {@code null} if {@code discordAppId} is missing/blank/not a valid numeric Discord application ID. */
    public static DiscordPresenceService forAppId(String discordAppId) {
        if (discordAppId == null || discordAppId.isBlank()) {
            return null;
        }
        try {
            return new DiscordPresenceService(Long.parseLong(discordAppId.trim()));
        } catch (NumberFormatException e) {
            Log.warn(DiscordPresenceService.class, "Invalid discord_app_id, ignoring: " + discordAppId);
            return null;
        }
    }

    /**
     * Attempts an immediate connection to the local Discord client over IPC, then keeps retrying
     * every {@value #RECONNECT_INTERVAL_SECONDS}s (in the background) until it succeeds or
     * {@link #close()} is called - covers both "Discord isn't running yet" and "Discord was
     * running, then quit/restarted mid-session". Never throws.
     */
    public void connect() {
        attemptConnect();
        scheduler.scheduleWithFixedDelay(this::reconnectIfNeeded,
                RECONNECT_INTERVAL_SECONDS, RECONNECT_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    private void reconnectIfNeeded() {
        if (!isConnected()) {
            attemptConnect();
        }
    }

    private void attemptConnect() {
        Future<IPCClient> future = connectWorker.submit(() -> {
            IPCClient newClient = new IPCClient(clientId);
            newClient.connect();
            return newClient;
        });
        try {
            client = future.get(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            Log.info(DiscordPresenceService.class, "Connected to Discord (Rich Presence).");
            Runnable activity = pendingActivity;
            if (activity != null) {
                activity.run();
            }
        } catch (TimeoutException e) {
            future.cancel(true);
            client = null;
            Log.warn(DiscordPresenceService.class, "Discord connection attempt timed out after "
                    + CONNECT_TIMEOUT_SECONDS + "s, will retry in " + RECONNECT_INTERVAL_SECONDS + "s");
        } catch (Throwable t) {
            client = null;
            Log.warn(DiscordPresenceService.class, "Could not connect to Discord, will retry in "
                    + RECONNECT_INTERVAL_SECONDS + "s (probably not running yet): " + t.getMessage());
        }
    }

    private boolean isConnected() {
        IPCClient current = client;
        return current != null && current.getStatus() == PipeStatus.CONNECTED;
    }

    /** Shows "Parcourt le launcher" - the idle/browsing state. Never throws. */
    public void setBrowsing() {
        pendingActivity = this::doSetBrowsing;
        doSetBrowsing();
    }

    private void doSetBrowsing() {
        setActivity("Parcourt le launcher", null);
    }

    /** Shows "Joue a <modpackName>" with an elapsed-time counter starting now. Never throws. */
    public void setPlaying(String modpackName) {
        pendingActivity = () -> doSetPlaying(modpackName);
        doSetPlaying(modpackName);
    }

    private void doSetPlaying(String modpackName) {
        setActivity("Joue a " + modpackName, OffsetDateTime.now());
    }

    private void setActivity(String details, OffsetDateTime startedAt) {
        IPCClient current = client;
        if (current == null) {
            // Not connected right now - doSetBrowsing/doSetPlaying above already got queued as
            // pendingActivity and will run again as soon as a reconnect succeeds.
            return;
        }
        try {
            RichPresence.Builder presence = new RichPresence.Builder().setDetails(details);
            if (startedAt != null) {
                presence.setStartTimestamp(startedAt);
            }
            current.sendRichPresence(presence.build());
        } catch (Throwable t) {
            Log.warn(DiscordPresenceService.class, "Could not update Discord presence: " + t.getMessage());
        }
    }

    /** Best-effort disconnect - safe to call even if {@link #connect()} was never called or already failed. Never throws. */
    public void close() {
        scheduler.shutdownNow();
        connectWorker.shutdownNow();
        pendingActivity = null;
        IPCClient current = client;
        client = null;
        if (current != null) {
            try {
                current.close();
            } catch (Throwable t) {
                Log.warn(DiscordPresenceService.class, "Could not cleanly close the Discord connection: " + t.getMessage());
            }
        }
    }
}
