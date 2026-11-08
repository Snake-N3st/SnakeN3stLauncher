/**
 * The signed-request recipe ({@link mc.snakenest.launcher.auth.SignedRequestSigner}) shared by
 * {@code launcher} and {@code bootstrap} - the rest of the device-flow login (challenge/poll
 * orchestration, player-info DTOs) stays in {@code launcher}'s own {@code auth} package, since
 * {@code bootstrap} only ever needs to sign a request, never fetch player-info/branding. Depends
 * only on {@code crypto} - no network, no UI.
 */
package mc.snakenest.launcher.auth;
