package mc.snakenest.launcher.news;

import mc.snakenest.launcher.net.HttpJsonClient;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Real check against the local dev Azuriom instance (see plan). Skips itself if unreachable. */
class NewsApiClientLocalAzuriomSmokeTest {

    private static final URI BASE = URI.create("http://127.0.0.1");

    @Test
    void listPostsDoesNotThrow() throws Exception {
        Assumptions.assumeTrue(isReachable(), "Local Azuriom dev instance not reachable, skipping");

        List<Post> posts = new NewsApiClient(new HttpJsonClient(), BASE).listPosts();

        assertNotNull(posts);
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
