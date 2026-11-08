/**
 * Device-flow login (challenge → browser confirmation → poll → store the
 * key) and the signed player-info lookups, per
 * {@code LAUNCHER_INTEGRATION.md} sections 1-3 and 6 in the sibling
 * {@code SnakeN3stLogin} repo. Depends on {@code crypto}, {@code common}'s own
 * {@code auth} package, and {@code net}. The signing recipe itself
 * ({@link mc.snakenest.launcher.auth.SignedRequestSigner}) lives in {@code common} now, shared
 * with {@code bootstrap} - see this package's README.
 */
package mc.snakenest.launcher.auth;
