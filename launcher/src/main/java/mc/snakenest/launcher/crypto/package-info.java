/**
 * The optional PIN code ({@link mc.snakenest.launcher.crypto.PinProtection}) - everything else
 * that used to live here (key handling, encrypted-at-rest storage) moved to {@code common}'s own
 * {@code mc.snakenest.launcher.crypto} package so {@code bootstrap} can reuse it too. See this
 * package's README for why.
 */
package mc.snakenest.launcher.crypto;
