package mc.snakenest.launcher.bootstrap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import mc.snakenest.launcher.auth.SignedParams;
import mc.snakenest.launcher.crypto.SignatureVerificationException;
import mc.snakenest.launcher.crypto.SignedArtifactStatement;
import mc.snakenest.launcher.util.AtomicFiles;
import mc.snakenest.launcher.util.Log;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Talks to the site's {@code launcher-auth} plugin release endpoints (see
 * {@code LAUNCHER_INTEGRATION.md} section 8). Deliberately independent of
 * {@code launcher.net.HttpJsonClient} - this module must stay free of any
 * dependency on the {@code launcher} module (which embeds GPL libraries),
 * so it talks HTTP directly rather than sharing that client. {@code
 * common.auth.SignedParams} is fine to depend on though - {@code common}
 * carries no GPL code of its own, see {@code THIRD-PARTY-NOTICES.md}.
 */
final class LauncherReleaseClient {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final URI baseUrl;
    private final String clientId;
    private final ArtifactVerifier verifier;
    private final TrustState trust;

    /** A client that verifies nothing - what every caller and test that predates signatures gets. */
    LauncherReleaseClient(URI baseUrl, String clientId) {
        this(baseUrl, clientId, ArtifactVerifier.disabled(), TrustState.inMemory());
    }

    LauncherReleaseClient(URI baseUrl, String clientId, ArtifactVerifier verifier, TrustState trust) {
        this.baseUrl = baseUrl;
        this.clientId = clientId;
        this.verifier = verifier;
        this.trust = trust;
    }

    /**
     * A release together with what the client concluded about its signature. The conclusion is computed
     * here, from the keys this build pins, and is never read from the response: a server can put any field it
     * likes in its JSON, so nothing in {@link LauncherReleaseInfo} can be taken as a verdict.
     *
     * @param status    for the jar itself: {@link SignatureStatus#VERIFIED}, {@link SignatureStatus#UNSIGNED}
     *                  (warn the player, let them go on) or {@link SignatureStatus#NOT_CHECKED}
     * @param debStatus for the Debian package of a bootstrap release, {@code null} when there is none; here
     *                  {@link SignatureStatus#REFUSED} is possible, and means "do not install that package"
     */
    record Checked(LauncherReleaseInfo info, SignatureStatus status, SignatureStatus debStatus) {
    }

    /**
     * @param type    {@link ReleaseType#LAUNCHER} or {@link ReleaseType#BOOTSTRAP} - see its Javadoc.
     * @param channel distribution channel to resolve "latest" through (see
     *                {@code config.LauncherConfig#channel} in the launcher module, read by
     *                {@code BootstrapMain} from the same config file) - {@code null} means the
     *                site's default channel. Ignored server-side for {@link ReleaseType#BOOTSTRAP}:
     *                the bootstrap is deliberately a single, minimal, pre-tested stream with no
     *                channel concept of its own.
     * @param signed  proof of eligibility for a restricted channel, or {@code null} for an
     *                unsigned first attempt - see {@link BootstrapMain}'s restricted-channel
     *                fallback flow, which retries with this populated once a 401/403 is hit.
     * @throws RestrictedChannelException on 401 (no valid signature) or 403 (signed but
     *                                    ineligible) - the resolved channel is restricted.
     */
    LauncherReleaseInfo fetchLatest(ReleaseType type, String channel, SignedParams signed)
            throws IOException, InterruptedException, BootstrapException {
        return fetchLatestChecked(type, channel, signed).info();
    }

    /**
     * {@link #fetchLatest}, plus the signature verdict. <b>The one place signatures are checked</b>: right after
     * the response is parsed and before the caller has a download URL to use, so nothing is fetched on the
     * strength of unauthenticated metadata, and the launcher jar, the bootstrap jar and the Debian package are
     * all covered by the same code.
     *
     * <p>{@code download()} and {@code downloadDeb()} are untouched, and that is the point worth understanding:
     * they already write through {@code AtomicFiles.writeVerified(target, body, release.sha256())}, which binds
     * the bytes to a digest. That digest used to be merely asserted by the site; once the signature over it has
     * been checked here, it is authenticated, and the bytes that reach the disk are the bytes that were signed.
     *
     * <p>What a signature that does not check out means (see also {@link TrustState}):
     * <ul>
     *   <li>valid - continue, recording that one has been seen;</li>
     *   <li>present but wrong - {@link SignatureVerificationException}, always;</li>
     *   <li>absent, but one was verified before - the same exception: that is a downgrade;</li>
     *   <li>absent, none ever verified - {@link SignatureStatus#UNSIGNED}: the caller warns.</li>
     * </ul>
     *
     * @throws SignatureVerificationException when the release must not be used
     */
    Checked fetchLatestChecked(ReleaseType type, String channel, SignedParams signed)
            throws IOException, InterruptedException, BootstrapException {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("client_id", clientId);
        if (type == ReleaseType.LAUNCHER && channel != null) {
            params.put("channel", channel);
        }
        if (signed != null) {
            params.putAll(signed.toQueryParams());
        }
        URI uri = URI.create(baseUrl.resolve("/api/launcher-auth/releases/" + type.apiValue() + "/latest") + "?" + toQueryString(params));
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(TIMEOUT).header("Accept", "application/json").GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 401 || response.statusCode() == 403) {
            throw new RestrictedChannelException("Le canal demandé est restreint (status " + response.statusCode() + ")");
        }
        if (response.statusCode() != 200) {
            throw new BootstrapException("Could not fetch the latest " + type.apiValue() + " release (status " + response.statusCode() + ")");
        }
        LauncherReleaseInfo info = GSON.fromJson(response.body(), LauncherReleaseInfo.class);

        if (info == null) {
            throw new BootstrapException("The site answered with an empty " + type.apiValue() + " release description.");
        }

        return verify(type, info);
    }

    private Checked verify(ReleaseType type, LauncherReleaseInfo info) throws SignatureVerificationException {
        String artifact = type == ReleaseType.LAUNCHER ? SignedArtifactStatement.TYPE_LAUNCHER : SignedArtifactStatement.TYPE_BOOTSTRAP;

        SignatureStatus status = check(artifact, info.version(), info.sha256(), info.size(), info.signature(), true);
        SignatureStatus debStatus = null;

        if (info.deb() != null && type == ReleaseType.BOOTSTRAP) {
            var deb = info.deb();
            // A package recorded without its bundled version cannot have its statement rebuilt: it is
            // "unsigned" as far as this client can tell, which is the transition's answer, not an error.
            var signature = deb.version() == null ? null : deb.signature();

            try {
                debStatus = check(SignedArtifactStatement.TYPE_DEB, deb.version() == null ? "0" : deb.version(),
                        deb.sha256(), deb.size(), signature, false);
            } catch (SignatureVerificationException e) {
                Log.error(LauncherReleaseClient.class, "The Debian package of bootstrap " + info.version() + " is refused: " + e.getMessage(), null);
                debStatus = SignatureStatus.REFUSED;
            }
        }

        return new Checked(info, status, debStatus);
    }

    private SignatureStatus check(String artifact, String version, String sha256, long size,
                                  LauncherReleaseInfo.Signature signature, boolean warnOnRollback) throws SignatureVerificationException {
        if (!verifier.isConfiguredFor(artifact)) {
            return SignatureStatus.NOT_CHECKED;
        }

        ArtifactVerifier.Verdict verdict = verifier.check(artifact, version, sha256, size, signature);

        switch (verdict) {
            case OK -> {
                String newest = trust.highestVersion(artifact);

                if (warnOnRollback && newest != null && VersionCompare.compare(version, newest) < 0) {
                    // Legitimate after a withdrawn release, and exactly what a replay of an old signed
                    // jar looks like: loud in the log, never a refusal - see TrustState.
                    Log.warn(LauncherReleaseClient.class, "The " + artifact + " release " + version
                            + " is older than " + newest + ", which was verified earlier on this installation.");
                }

                trust.recordValid(artifact, version);
                return SignatureStatus.VERIFIED;
            }
            case INVALID -> throw new SignatureVerificationException(
                    "The signature of the " + artifact + " release " + version + " is not valid: it was not made by a key this launcher trusts, "
                            + "or does not cover this version and digest.");
            default -> {
                if (trust.hasSeenValid(artifact)) {
                    throw new SignatureVerificationException(
                            "The " + artifact + " release " + version + " is not signed, although signed releases have been seen before: "
                                    + "refusing what looks like a downgrade.");
                }

                Log.warn(LauncherReleaseClient.class, "The " + artifact + " release " + version
                        + " is not signed, and no signed release has been seen yet on this installation.");
                return SignatureStatus.UNSIGNED;
            }
        }
    }

    /**
     * Streams the release jar to {@code target}, verifying its SHA-256 before it's ever committed
     * there. {@code signed} must be the same (or a freshly re-signed) proof used for the
     * {@link #fetchLatest} call that returned {@code release}, if that one needed one - a
     * restricted release is gated here too, not just on the metadata lookup.
     */
    void download(LauncherReleaseInfo release, Path target, SignedParams signed) throws IOException, InterruptedException, BootstrapException {
        Map<String, String> params = signed != null ? new LinkedHashMap<>(signed.toQueryParams()) : new LinkedHashMap<>();
        params.put("client_id", clientId);
        String separator = release.downloadUrl().contains("?") ? "&" : "?";
        URI uri = URI.create(release.downloadUrl() + separator + toQueryString(params));
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(TIMEOUT).GET().build();
        HttpResponse<java.io.InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() == 401 || response.statusCode() == 403) {
            throw new RestrictedChannelException("Le canal demandé est restreint (status " + response.statusCode() + ")");
        }
        if (response.statusCode() != 200) {
            throw new BootstrapException("Could not download launcher release " + release.version() + " (status " + response.statusCode() + ")");
        }
        AtomicFiles.writeVerified(target, response.body(), release.sha256());
    }

    /**
     * Streams the Debian package to {@code target}, verifying its SHA-256 before it is committed
     * there - same guarantee as {@link #download}, and it matters more here: these bytes are about
     * to be handed to a package manager running as root.
     *
     * <p>Unsigned and without a {@code client_id}, unlike every other endpoint this class calls:
     * {@code /launcher-download/deb} is the same public route the download page and the one-line
     * bash installer use (see the site's {@code RouteServiceProvider}), because a player installing
     * the package for the first time has no client id yet. Nothing is lost by not sending one - the
     * package is public either way.
     */
    void downloadDeb(LauncherReleaseInfo.DebPackage deb, Path target) throws IOException, InterruptedException, BootstrapException {
        URI uri = baseUrl.resolve(deb.downloadUrl());
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(TIMEOUT).GET().build();
        HttpResponse<java.io.InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() != 200) {
            throw new BootstrapException("Could not download the Debian package (status " + response.statusCode() + ")");
        }
        AtomicFiles.writeVerified(target, response.body(), deb.sha256());
    }

    private static String toQueryString(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        for (var entry : params.entrySet()) {
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(encode(entry.getKey())).append('=').append(encode(entry.getValue()));
        }
        return sb.toString();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
