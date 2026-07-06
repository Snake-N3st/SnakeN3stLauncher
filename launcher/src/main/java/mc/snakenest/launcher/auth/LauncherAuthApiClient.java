package mc.snakenest.launcher.auth;

import mc.snakenest.launcher.net.HttpJsonClient;
import mc.snakenest.launcher.net.JsonResponse;
import mc.snakenest.launcher.net.Uris;

import java.io.IOException;
import java.net.URI;
import java.util.Map;

/**
 * Client for the {@code launcher-auth} plugin's endpoints: the device-flow
 * challenge (obtain/poll) and the three signed player-info lookups. See
 * {@code LAUNCHER_INTEGRATION.md} sections 1-3 and 6 in the sibling
 * {@code SnakeN3stLogin} repo for the authoritative contract.
 */
public final class LauncherAuthApiClient {

    private final HttpJsonClient http;
    private final URI baseUrl;

    public LauncherAuthApiClient(HttpJsonClient http, URI baseUrl) {
        this.http = http;
        this.baseUrl = baseUrl;
    }

    /** @return the challenge token to show the user's browser and to poll */
    public String requestChallenge(String clientId) throws IOException, InterruptedException, LauncherApiException {
        JsonResponse response = http.postJson(baseUrl.resolve("/api/launcher-auth/challenge"), Map.of("client_id", clientId));

        if (response.statusCode() == 404) {
            throw new LauncherApiException("Unknown client_id");
        }
        if (!response.isSuccess()) {
            throw new LauncherApiException("Unexpected status requesting a challenge: " + response.statusCode());
        }
        return response.as(ChallengeResponse.class).challenge();
    }

    public URI loginUrl(String challengeToken) {
        return Uris.withQuery(baseUrl.resolve("/launcher-login"), Map.of("challenge", challengeToken));
    }

    public PollResult pollChallenge(String challengeToken) throws IOException, InterruptedException, LauncherApiException {
        JsonResponse response = http.get(baseUrl.resolve("/api/launcher-auth/challenge/" + challengeToken));

        return switch (response.statusCode()) {
            case 200 -> {
                PollSuccessResponse body = response.as(PollSuccessResponse.class);
                yield new PollResult.Success(body.privateKey(), body.playerId());
            }
            case 403 -> new PollResult.Pending();
            case 404 -> new PollResult.NotFound();
            default -> throw new LauncherApiException("Unexpected status polling the challenge: " + response.statusCode());
        };
    }

    public String fetchUsername(PlayerSession session) throws IOException, InterruptedException, LauncherApiException {
        return fetchPlayerInfo("/api/launcher-auth/player/username", session, UsernameResponse.class).username();
    }

    public String fetchRole(PlayerSession session) throws IOException, InterruptedException, LauncherApiException {
        return fetchPlayerInfo("/api/launcher-auth/player/role", session, RoleResponse.class).role();
    }

    public String fetchEmail(PlayerSession session) throws IOException, InterruptedException, LauncherApiException {
        return fetchPlayerInfo("/api/launcher-auth/player/email", session, EmailResponse.class).email();
    }

    private <T> T fetchPlayerInfo(String path, PlayerSession session, Class<T> type)
            throws IOException, InterruptedException, LauncherApiException {
        SignedParams signed = SignedRequestSigner.sign(session);
        URI uri = Uris.withQuery(baseUrl.resolve(path), signed.toQueryParams());
        JsonResponse response = http.get(uri);

        if (!response.isSuccess()) {
            throw new LauncherApiException("Player info request rejected (status " + response.statusCode() + ")");
        }
        return response.as(type);
    }

    private record ChallengeResponse(String challenge) {
    }

    private record PollSuccessResponse(String privateKey, long playerId) {
    }

    private record UsernameResponse(String username) {
    }

    private record RoleResponse(String role) {
    }

    private record EmailResponse(String email) {
    }
}
