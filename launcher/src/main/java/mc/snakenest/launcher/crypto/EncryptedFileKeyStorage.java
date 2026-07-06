package mc.snakenest.launcher.crypto;

import mc.snakenest.launcher.util.AppDirs;
import mc.snakenest.launcher.util.AtomicFiles;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Optional;

/**
 * Stores the player's Ed25519 seed encrypted at rest (AES-256-GCM) under
 * {@link AppDirs#secure()}, with the encryption key kept in a separate file,
 * both permission-restricted via {@link SecureFilePermissions}. See that
 * class's javadoc for what this does and doesn't protect against.
 */
public final class EncryptedFileKeyStorage implements KeyStorage {

    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int AES_KEY_LENGTH_BYTES = 32;

    private final Path wrappingKeyFile;
    private final Path encryptedKeyFile;
    private final SecureRandom random = new SecureRandom();

    public EncryptedFileKeyStorage(AppDirs dirs) {
        this.wrappingKeyFile = dirs.secure().resolve("wrap.key");
        this.encryptedKeyFile = dirs.secure().resolve("key.bin");
    }

    @Override
    public void save(Ed25519KeyPair keyPair) throws KeyStorageException {
        try {
            SecretKeySpec wrappingKey = loadOrCreateWrappingKey();

            byte[] iv = new byte[GCM_IV_LENGTH];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, wrappingKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(keyPair.seed());

            byte[] out = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ciphertext, 0, out, iv.length, ciphertext.length);

            AtomicFiles.write(encryptedKeyFile, out);
            SecureFilePermissions.restrictToOwner(encryptedKeyFile);
        } catch (IOException | GeneralSecurityException e) {
            throw new KeyStorageException("Could not save the encrypted key", e);
        }
    }

    @Override
    public Optional<Ed25519KeyPair> load() throws KeyStorageException {
        if (!Files.isRegularFile(encryptedKeyFile) || !Files.isRegularFile(wrappingKeyFile)) {
            return Optional.empty();
        }
        try {
            SecretKeySpec wrappingKey = loadOrCreateWrappingKey();
            byte[] stored = Files.readAllBytes(encryptedKeyFile);
            if (stored.length <= GCM_IV_LENGTH) {
                throw new KeyStorageException("Stored key file is truncated");
            }
            byte[] iv = Arrays.copyOfRange(stored, 0, GCM_IV_LENGTH);
            byte[] ciphertext = Arrays.copyOfRange(stored, GCM_IV_LENGTH, stored.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, wrappingKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] seed = cipher.doFinal(ciphertext);

            return Optional.of(Ed25519KeyPair.fromSeed(seed));
        } catch (IOException | GeneralSecurityException e) {
            throw new KeyStorageException("Could not read the encrypted key", e);
        }
    }

    @Override
    public void delete() {
        try {
            Files.deleteIfExists(encryptedKeyFile);
        } catch (IOException e) {
            // Best-effort: a leftover encrypted file with no valid session is harmless, not a
            // correctness issue - logout should not fail just because the disk was uncooperative.
        }
    }

    private SecretKeySpec loadOrCreateWrappingKey() throws IOException {
        if (Files.isRegularFile(wrappingKeyFile)) {
            return new SecretKeySpec(Files.readAllBytes(wrappingKeyFile), "AES");
        }
        byte[] key = new byte[AES_KEY_LENGTH_BYTES];
        random.nextBytes(key);
        AtomicFiles.write(wrappingKeyFile, key);
        SecureFilePermissions.restrictToOwner(wrappingKeyFile);
        return new SecretKeySpec(key, "AES");
    }
}
