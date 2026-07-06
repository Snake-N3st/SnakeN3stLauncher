package mc.snakenest.launcher.auth;

import mc.snakenest.launcher.crypto.Ed25519KeyPair;
import mc.snakenest.launcher.crypto.KeyStorage;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Orchestrates the whole device-flow login: request a challenge, open the
 * user's browser to confirm it, poll until the key is delivered (or the
 * challenge dies), then store the key. Runs entirely on one background
 * thread so the caller (the login screen) never blocks.
 */
public final class DeviceAuthService {

    private static final Duration POLL_INTERVAL = Duration.ofMillis(2500);
    // The server's own challenge TTL is 10 minutes; this is a client-side backstop
    // in case a poll never comes back 404 for some reason, not a real protocol value.
    private static final Duration MAX_POLL_DURATION = Duration.ofMinutes(11);

    private final LauncherAuthApiClient api;
    private final KeyStorage keyStorage;
    private final String clientId;
    private final BrowserOpener browserOpener;
    private final ExecutorService executor;

    private volatile boolean cancelled;

    public DeviceAuthService(LauncherAuthApiClient api, KeyStorage keyStorage, String clientId) {
        this(api, keyStorage, clientId, BrowserOpener.DESKTOP);
    }

    /** @param browserOpener lets tests substitute a no-op instead of really opening a browser */
    public DeviceAuthService(LauncherAuthApiClient api, KeyStorage keyStorage, String clientId, BrowserOpener browserOpener) {
        this.api = api;
        this.keyStorage = keyStorage;
        this.clientId = clientId;
        this.browserOpener = browserOpener;
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "device-auth");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** Starts (or restarts) the login flow. Callbacks fire on a background thread. */
    public void start(DeviceAuthListener listener) {
        cancelled = false;
        executor.execute(() -> runFlow(listener));
    }

    /** Stops polling. The challenge itself simply expires server-side. */
    public void cancel() {
        cancelled = true;
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    private void runFlow(DeviceAuthListener listener) {
        listener.onStateChanged(DeviceAuthState.REQUESTING_CHALLENGE);

        String token;
        try {
            token = api.requestChallenge(clientId);
        } catch (IOException | InterruptedException | LauncherApiException e) {
            fail(listener, "Could not request a login challenge: " + e.getMessage());
            return;
        }

        listener.onStateChanged(DeviceAuthState.AWAITING_USER_CONFIRMATION);
        openBrowser(api.loginUrl(token), listener);

        listener.onStateChanged(DeviceAuthState.POLLING);
        pollUntilDone(token, listener);
    }

    private void openBrowser(URI loginUrl, DeviceAuthListener listener) {
        if (!browserOpener.open(loginUrl)) {
            listener.onBrowserOpenFailed(loginUrl.toString());
        }
    }

    private void pollUntilDone(String token, DeviceAuthListener listener) {
        Instant deadline = Instant.now().plus(MAX_POLL_DURATION);

        while (!cancelled && Instant.now().isBefore(deadline)) {
            PollResult result;
            try {
                result = api.pollChallenge(token);
            } catch (IOException | InterruptedException | LauncherApiException e) {
                fail(listener, "Error while polling for login confirmation: " + e.getMessage());
                return;
            }

            if (result instanceof PollResult.Success success) {
                handleSuccess(success, listener);
                return;
            }
            if (result instanceof PollResult.NotFound) {
                fail(listener, "Login challenge expired or was not confirmed in time.");
                return;
            }
            // Pending: fall through and poll again after the interval.

            if (!sleepUnlessCancelled()) {
                break;
            }
        }

        if (cancelled) {
            listener.onStateChanged(DeviceAuthState.CANCELLED);
        } else {
            fail(listener, "Timed out waiting for login confirmation.");
        }
    }

    private void handleSuccess(PollResult.Success success, DeviceAuthListener listener) {
        try {
            Ed25519KeyPair keyPair = Ed25519KeyPair.fromSeedHex(success.seedHex());
            keyStorage.save(keyPair);
        } catch (Exception e) {
            fail(listener, "Received a login key but could not store it securely: " + e.getMessage());
            return;
        }
        listener.onStateChanged(DeviceAuthState.SUCCEEDED);
        listener.onSucceeded(success.playerId());
    }

    private void fail(DeviceAuthListener listener, String reason) {
        listener.onStateChanged(DeviceAuthState.FAILED);
        listener.onFailed(reason);
    }

    /** @return false if interrupted/cancelled while sleeping */
    private boolean sleepUnlessCancelled() {
        try {
            Thread.sleep(POLL_INTERVAL.toMillis());
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            cancelled = true;
            return false;
        }
    }
}
