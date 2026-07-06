package mc.snakenest.launcher.auth;

import mc.snakenest.launcher.net.HttpJsonClient;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Exercises the real {@code launcher-auth} plugin API on the local dev
 * Azuriom instance (see plan's "Environnement de test local") - the actual
 * PHP challenge controller, not a stand-in. Only covers what doesn't need a
 * human to click "confirm" in a browser (see auth/README.md for the manual
 * milestone that does). Skips itself if the local instance, or the
 * dev-test LauncherClient this test depends on, isn't present - both are
 * local-only fixtures, not something every machine running this suite has.
 */
class LauncherAuthApiClientLocalAzuriomSmokeTest {

    private static final URI BASE = URI.create("http://127.0.0.1");
    // Created once via `php artisan tinker` against the local dev instance for this test;
    // not a secret (client_id isn't sensitive, see LAUNCHER_INTEGRATION.md).
    private static final String DEV_TEST_CLIENT_ID = "QnKK3ntjDXHfQ5PhQCQxMJpR85LNQqd2";

    @Test
    void unknownClientIdIsRejected() throws Exception {
        Assumptions.assumeTrue(isReachable(), "Local Azuriom dev instance not reachable, skipping");

        LauncherAuthApiClient api = new LauncherAuthApiClient(new HttpJsonClient(), BASE);

        LauncherApiException exception = org.junit.jupiter.api.Assertions.assertThrows(
                LauncherApiException.class,
                () -> api.requestChallenge("this-client-id-does-not-exist")
        );
        assertFalse(exception.getMessage().isBlank());
    }

    @Test
    void aFreshChallengeIsPendingUntilConfirmed() throws Exception {
        Assumptions.assumeTrue(isReachable(), "Local Azuriom dev instance not reachable, skipping");
        Assumptions.assumeTrue(devTestClientExists(), "dev-test-launcher-client not set up locally, skipping");

        LauncherAuthApiClient api = new LauncherAuthApiClient(new HttpJsonClient(), BASE);

        String token = api.requestChallenge(DEV_TEST_CLIENT_ID);
        assertFalse(token.isBlank());

        PollResult result = api.pollChallenge(token);
        assertInstanceOf(PollResult.Pending.class, result);
    }

    @Test
    void pollingAnUnknownTokenIsNotFound() throws Exception {
        Assumptions.assumeTrue(isReachable(), "Local Azuriom dev instance not reachable, skipping");

        LauncherAuthApiClient api = new LauncherAuthApiClient(new HttpJsonClient(), BASE);

        PollResult result = api.pollChallenge("00000000-0000-0000-0000-000000000000");

        assertInstanceOf(PollResult.NotFound.class, result);
    }

    private static boolean isReachable() {
        try {
            HttpClient probe = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
            HttpRequest request = HttpRequest.newBuilder(BASE).timeout(Duration.ofSeconds(2)).GET().build();
            probe.send(request, HttpResponse.BodyHandlers.discarding());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean devTestClientExists() throws Exception {
        LauncherAuthApiClient api = new LauncherAuthApiClient(new HttpJsonClient(), BASE);
        try {
            api.requestChallenge(DEV_TEST_CLIENT_ID);
            return true;
        } catch (LauncherApiException e) {
            return false;
        }
    }
}
