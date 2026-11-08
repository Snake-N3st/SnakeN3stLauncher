package mc.snakenest.launcher.bootstrap;

import mc.snakenest.launcher.util.Log;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;

/**
 * The strong warning shown when a launcher release carries no signature and none has ever been verified on
 * this installation - the transition, and the only time the player is asked. See {@link TrustState} for why it
 * cannot last: one verified signature anywhere turns every later absence into a refusal, which is what keeps
 * this question from being clicked through by habit.
 *
 * <p>Same headless-safe, blocking {@code JOptionPane} style as {@link ChannelFallbackDialog}. With no display
 * there is nobody to ask, and this project's rule is to warn rather than block when a signature cannot be
 * checked: the answer is "go on", with the warning already in the log.
 */
final class UnverifiedReleaseDialog {

    private UnverifiedReleaseDialog() {
    }

    /** @return {@code true} when the launch may go on */
    static boolean confirmLaunch(String version) {
        if (GraphicsEnvironment.isHeadless()) {
            Log.warn(UnverifiedReleaseDialog.class, "No display to ask on: going on with the unsigned launcher " + version + ".");
            return true;
        }

        String message = "Impossible de vérifier l'authenticité de cette version du launcher (" + version + ") : "
                + "elle n'est pas signée.\n\n"
                + "C'est normal pendant la transition vers les versions signées, mais cela peut aussi signifier que "
                + "le site a été modifié par quelqu'un d'autre.\n\n"
                + "Lancer quand même ?";
        Object[] options = {"Lancer quand même", "Annuler"};
        int[] choice = {JOptionPane.CLOSED_OPTION};

        try {
            SwingUtilities.invokeAndWait(() -> choice[0] = JOptionPane.showOptionDialog(
                    null, message, "SnakeN3st Launcher",
                    JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE,
                    // The safe answer is the default one.
                    null, options, options[1]));
        } catch (Exception e) {
            return false;
        }

        return choice[0] == 0;
    }
}
