package mc.snakenest.launcher.net;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A real end-to-end check against the local dev Azuriom instance
 * (http://127.0.0.1, see plan's "Environnement de test local"). Skips itself
 * (rather than failing) when that instance isn't reachable, since it won't
 * be on another machine or in CI - this is a development milestone check,
 * not a correctness guarantee covered elsewhere.
 */
class HttpJsonClientLocalAzuriomSmokeTest {

    private static final URI BASE = URI.create("http://127.0.0.1");

    @Test
    void apiPostsRespondsWithJson() throws Exception {
        Assumptions.assumeTrue(isReachable(), "Local Azuriom dev instance not reachable, skipping");

        HttpJsonClient client = new HttpJsonClient();
        JsonResponse response = client.get(BASE.resolve("/api/posts"));

        assertEquals(200, response.statusCode());
        // Confirmed shape via curl earlier in this project: a JSON array (possibly empty).
        assertTrue(response.rawBody().trim().startsWith("["));
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
}
