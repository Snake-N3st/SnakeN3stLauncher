package mc.snakenest.launcher.auth;

import mc.snakenest.launcher.crypto.Ed25519KeyPair;
import mc.snakenest.launcher.crypto.KeyStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceAuthServiceTest {

    private static final String SEED_HEX = "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f";

    @Mock
    private LauncherAuthApiClient api;

    @Mock
    private KeyStorage keyStorage;

    // Never the real Desktop-backed one: an unmocked Desktop.browse() call in a test opens a
    // real browser tab every time the suite runs - found this out the hard way (see BrowserOpener's javadoc).
    private final List<java.net.URI> openedUrls = Collections.synchronizedList(new java.util.ArrayList<>());
    private final BrowserOpener noOpBrowserOpener = uri -> {
        openedUrls.add(uri);
        return true;
    };

    private final List<DeviceAuthState> states = Collections.synchronizedList(new java.util.ArrayList<>());

    private final class RecordingListener implements DeviceAuthListener {
        final CountDownLatch done = new CountDownLatch(1);
        Long succeededPlayerId;
        String failureReason;

        @Override
        public void onStateChanged(DeviceAuthState state) {
            states.add(state);
        }

        @Override
        public void onBrowserOpenFailed(String loginUrl) {
        }

        @Override
        public void onSucceeded(long playerId) {
            succeededPlayerId = playerId;
            done.countDown();
        }

        @Override
        public void onFailed(String reasonMessage) {
            failureReason = reasonMessage;
            done.countDown();
        }
    }

    @Test
    void successfulLoginStoresTheKeyAndNotifiesTheListener() throws Exception {
        when(api.requestChallenge("client-1")).thenReturn("token-1");
        when(api.loginUrl("token-1")).thenReturn(java.net.URI.create("http://127.0.0.1/launcher-login?challenge=token-1"));
        when(api.pollChallenge("token-1")).thenReturn(new PollResult.Success(SEED_HEX, 42L));

        DeviceAuthService service = new DeviceAuthService(api, keyStorage, "client-1", noOpBrowserOpener);
        RecordingListener listener = new RecordingListener();

        service.start(listener);
        assertTrue(listener.done.await(5, TimeUnit.SECONDS));
        service.shutdown();

        assertEquals(42L, listener.succeededPlayerId);
        verify(keyStorage).save(any(Ed25519KeyPair.class));
        assertTrue(states.contains(DeviceAuthState.SUCCEEDED));
        assertEquals(List.of(java.net.URI.create("http://127.0.0.1/launcher-login?challenge=token-1")), openedUrls);
    }

    @Test
    void expiredChallengeFailsTheFlow() throws Exception {
        when(api.requestChallenge("client-1")).thenReturn("token-1");
        when(api.loginUrl("token-1")).thenReturn(java.net.URI.create("http://127.0.0.1/launcher-login?challenge=token-1"));
        when(api.pollChallenge("token-1")).thenReturn(new PollResult.NotFound());

        DeviceAuthService service = new DeviceAuthService(api, keyStorage, "client-1", noOpBrowserOpener);
        RecordingListener listener = new RecordingListener();

        service.start(listener);
        assertTrue(listener.done.await(5, TimeUnit.SECONDS));
        service.shutdown();

        assertTrue(listener.failureReason != null && listener.failureReason.contains("expired"));
        assertTrue(states.contains(DeviceAuthState.FAILED));
    }

    @Test
    void cancelStopsPollingWithoutFailing() throws Exception {
        when(api.requestChallenge("client-1")).thenReturn("token-1");
        when(api.loginUrl("token-1")).thenReturn(java.net.URI.create("http://127.0.0.1/launcher-login?challenge=token-1"));
        when(api.pollChallenge("token-1")).thenReturn(new PollResult.Pending());

        DeviceAuthService service = new DeviceAuthService(api, keyStorage, "client-1", noOpBrowserOpener);
        RecordingListener listener = new RecordingListener();

        service.start(listener);
        // Give it time to reach POLLING before cancelling.
        Thread.sleep(200);
        service.cancel();

        // cancel() doesn't itself signal onFailed/onSucceeded, only a state change; poll for it.
        long deadline = System.currentTimeMillis() + 5000;
        while (!states.contains(DeviceAuthState.CANCELLED) && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        service.shutdown();

        assertTrue(states.contains(DeviceAuthState.CANCELLED));
    }
}
