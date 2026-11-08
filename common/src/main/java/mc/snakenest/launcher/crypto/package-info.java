/**
 * Ed25519 key handling ({@link mc.snakenest.launcher.crypto.Ed25519KeyPair})
 * and encrypted-at-rest storage of the player's private key
 * ({@link mc.snakenest.launcher.crypto.KeyStorage}), shared by {@code launcher} and
 * {@code bootstrap}. Depends only on {@code util} and {@code net.i2p.crypto:eddsa} (CC0,
 * public domain) - no network, no UI, safe for {@code bootstrap} to depend on.
 */
package mc.snakenest.launcher.crypto;
