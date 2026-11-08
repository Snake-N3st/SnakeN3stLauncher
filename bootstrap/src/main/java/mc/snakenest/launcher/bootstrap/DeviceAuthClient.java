package mc.snakenest.launcher.bootstrap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import mc.snakenest.launcher.auth.PlayerSession;
import mc.snakenest.launcher.crypto.Ed25519KeyPair;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

/**
 * The bootstrap's own minimal device-flow login (challenge → browser confirmation → poll → the
 * resulting key), used only for the "Se reconnecter" option in {@link ChannelFallbackDialog} -
 * needed to prove eligibility for a restricted distribution channel when no usable local key
 * exists yet (see {@link BootstrapMain}).
 *
 * <p>Deliberately its own small implementation rather than reusing {@code launcher.auth.
 * DeviceAuthService} (the full, cancellable, listener-driven version the launcher's login screen
 * uses): that class pulls in {@code launcher.auth.LauncherAuthApiClient}, which itself depends on
 * {@code launcher.net.HttpJsonClient} and carries player-info/client-branding/revoke methods this
 * module has no use for - see {@code LauncherReleaseClient}'s own docblock for why {@code
 * bootstrap} stays independent of anything in the {@code launcher} module. Only {@code
 * common.crypto.Ed25519KeyPair} (needed to turn the delivered seed into a usable key) is shared.
 *
 * <p>Synchronous/blocking on purpose - unlike the launcher's login screen, there's no other UI
 * happening concurrently here to stay responsive for; the caller shows a small "waiting for
 * confirmation..." dialog and calls this from a background thread.
 */
final class DeviceAuthClient {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration POLL_INTERVAL = Duration.ofMillis(2500);
    // The server's own challenge TTL is 10 minutes; this is a client-side backstop, same value
    // and reasoning as launcher.auth.DeviceAuthService's own MAX_POLL_DURATION.
    private static final Duration MAX_POLL_DURATION = Duration.ofMinutes(11);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final URI baseUrl;
    private final String clientId;

    DeviceAuthClient(URI baseUrl, String clientId) {
        this.baseUrl = baseUrl;
        this.clientId = clientId;
    }

    /**
     * Runs the whole flow to completion: requests a challenge, opens the player's browser to
     * confirm it, polls until the key is delivered. Blocks the calling thread until success,
     * cancellation (via {@code isCancelled}), or the challenge expires.
     *
     * @param isCancelled polled between each wait - lets the caller's "Annuler" button actually
     *                    stop this from a different thread, rather than only being able to wait
     *                    out the full timeout.
     * @return a session ready to sign a request with, or empty if cancelled
     */
    java.util.Optional<PlayerSession> run(java.util.function.BooleanSupplier isCancelled) throws IOException, InterruptedException, BootstrapException {
        String token = requestChallenge();
        openBrowser(loginUrl(token));

        Instant deadline = Instant.now().plus(MAX_POLL_DURATION);
        while (Instant.now().isBefore(deadline)) {
            if (isCancelled.getAsBoolean()) {
                return java.util.Optional.empty();
            }
            PollResult result = pollChallenge(token);
            if (result instanceof PollResult.Success success) {
                Ed25519KeyPair key = Ed25519KeyPair.fromSeedHex(success.seedHex());
                return java.util.Optional.of(new PlayerSession(key, success.playerId(), clientId));
            }
            if (result instanceof PollResult.NotFound) {
                throw new BootstrapException("La demande de connexion a expiré ou n'a pas été confirmée à temps.");
            }
            Thread.sleep(POLL_INTERVAL.toMillis());
        }
        throw new BootstrapException("Délai dépassé en attendant la confirmation de connexion.");
    }

    private String requestChallenge() throws IOException, InterruptedException, BootstrapException {
        String body = GSON.toJson(java.util.Map.of("client_id", clientId));
        HttpRequest request = HttpRequest.newBuilder(baseUrl.resolve("/api/launcher-auth/challenge"))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new BootstrapException("Impossible de démarrer la reconnexion (status " + response.statusCode() + ")");
        }
        return GSON.fromJson(response.body(), ChallengeResponse.class).challenge();
    }

    private PollResult pollChallenge(String token) throws IOException, InterruptedException, BootstrapException {
        HttpRequest request = HttpRequest.newBuilder(baseUrl.resolve("/api/launcher-auth/challenge/" + token))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

        return switch (response.statusCode()) {
            case 200 -> {
                PollSuccessResponse body = GSON.fromJson(response.body(), PollSuccessResponse.class);
                yield new PollResult.Success(body.privateKey(), body.playerId());
            }
            case 403 -> new PollResult.Pending();
            case 404 -> new PollResult.NotFound();
            default -> throw new BootstrapException("Statut inattendu en attendant la confirmation (status " + response.statusCode() + ")");
        };
    }

    private URI loginUrl(String challengeToken) {
        return baseUrl.resolve("/launcher-login?challenge=" + URLEncoder.encode(challengeToken, StandardCharsets.UTF_8));
    }

    /** Best-effort - a failed/unsupported Desktop.browse() just leaves the URL unopened, the player can still be told the address. */
    private void openBrowser(URI uri) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(uri);
            }
        } catch (IOException ignored) {
        }
    }

    private sealed interface PollResult {
        record Pending() implements PollResult {
        }

        record NotFound() implements PollResult {
        }

        record Success(String seedHex, long playerId) implements PollResult {
        }
    }

    private record ChallengeResponse(String challenge) {
    }

    private record PollSuccessResponse(String privateKey, long playerId) {
    }
}
