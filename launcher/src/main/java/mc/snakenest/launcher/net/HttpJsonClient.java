package mc.snakenest.launcher.net;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import mc.snakenest.launcher.util.Log;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Thin wrapper over the JDK's built-in {@link HttpClient} (no separate HTTP
 * dependency needed) with one shared, consistently-configured {@link Gson}.
 *
 * <h2>Hard rule</h2>
 * Every log line in this class prints the request <b>path</b> only, never
 * the full URI. This API's query strings carry {@code signature}/
 * {@code publicKey}/{@code client_id}; logging them would defeat the point
 * of them being short-lived/scoped in the first place.
 */
public final class HttpJsonClient {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient httpClient;
    private final Gson gson;

    public HttpJsonClient() {
        this(new GsonBuilder().disableHtmlEscaping().create());
    }

    public HttpJsonClient(Gson gson) {
        this.gson = gson;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public JsonResponse get(URI uri) throws IOException, InterruptedException {
        HttpRequest request = requestBuilder(uri).GET().build();
        return sendForJson(request);
    }

    public JsonResponse postJson(URI uri, Object requestBody) throws IOException, InterruptedException {
        String json = gson.toJson(requestBody);
        HttpRequest request = requestBuilder(uri)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        return sendForJson(request);
    }

    /** For streaming (non-JSON) downloads. The caller must close the returned response. */
    public RawResponse getRaw(URI uri) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();
        Log.info(HttpJsonClient.class, "GET (raw) " + uri.getPath());
        HttpResponse<java.io.InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        return new RawResponse(response.statusCode(), response.body());
    }

    private HttpRequest.Builder requestBuilder(URI uri) {
        return HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json");
    }

    private JsonResponse sendForJson(HttpRequest request) throws IOException, InterruptedException {
        Log.info(HttpJsonClient.class, request.method() + " " + request.uri().getPath());
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return new JsonResponse(response.statusCode(), response.body(), gson);
    }
}
