package mc.snakenest.launcher.bootstrap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.awt.GraphicsEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * This suite (like every other test here) always runs headless (no real display) - confirms the
 * headless path returns immediately instead of hanging, which matters for a CI/server context
 * that could otherwise block forever waiting for a dialog no one can click.
 */
class ChannelFallbackDialogTest {

    @Test
    @Timeout(5)
    void showReturnsNoneImmediatelyWhenHeadless() {
        assumeTrue(GraphicsEnvironment.isHeadless(), "this test only means something in a headless environment");

        assertEquals(ChannelFallbackDialog.Choice.NONE, ChannelFallbackDialog.show("beta"));
    }

    @Test
    @Timeout(5)
    void showReconnectingDegradesToAnAlwaysNotCancelledNoOpWindowWhenHeadless() {
        assumeTrue(GraphicsEnvironment.isHeadless(), "this test only means something in a headless environment");

        var window = ChannelFallbackDialog.showReconnecting();

        assertFalse(window.isCancelled());
        window.close(); // must not throw
    }
}
