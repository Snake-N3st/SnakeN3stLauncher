package mc.snakenest.launcher.crypto;

import mc.snakenest.launcher.util.AppDirs;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EncryptedFileKeyStorageTest {

    private static final String SEED_HEX = "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f";

    @Test
    void loadIsEmptyWhenNothingWasEverSaved(@TempDir Path tempDir) throws KeyStorageException {
        KeyStorage storage = new EncryptedFileKeyStorage(new AppDirs(tempDir));

        assertTrue(storage.load().isEmpty());
    }

    @Test
    void saveThenLoadRoundTripsTheSameSeed(@TempDir Path tempDir) throws KeyStorageException {
        KeyStorage storage = new EncryptedFileKeyStorage(new AppDirs(tempDir));
        Ed25519KeyPair original = Ed25519KeyPair.fromSeedHex(SEED_HEX);

        storage.save(original);
        Optional<Ed25519KeyPair> loaded = storage.load();

        assertTrue(loaded.isPresent());
        assertEquals(original.seedHex(), loaded.get().seedHex());
        assertEquals(original.publicKeyHex(), loaded.get().publicKeyHex());
    }

    @Test
    void theStoredFileNeverContainsThePlaintextSeed(@TempDir Path tempDir) throws Exception {
        KeyStorage storage = new EncryptedFileKeyStorage(new AppDirs(tempDir));
        storage.save(Ed25519KeyPair.fromSeedHex(SEED_HEX));

        Path keyFile = tempDir.resolve("secure").resolve("key.bin");
        String rawContent = new String(Files.readAllBytes(keyFile), java.nio.charset.StandardCharsets.ISO_8859_1);

        assertFalse(rawContent.contains(new String(mc.snakenest.launcher.util.Hex.decode(SEED_HEX), java.nio.charset.StandardCharsets.ISO_8859_1)));
    }

    @Test
    void deleteRemovesTheStoredKey(@TempDir Path tempDir) throws KeyStorageException {
        KeyStorage storage = new EncryptedFileKeyStorage(new AppDirs(tempDir));
        storage.save(Ed25519KeyPair.fromSeedHex(SEED_HEX));

        storage.delete();

        assertTrue(storage.load().isEmpty());
    }

    @Test
    void deleteWhenNothingWasSavedDoesNotThrow(@TempDir Path tempDir) {
        KeyStorage storage = new EncryptedFileKeyStorage(new AppDirs(tempDir));

        storage.delete();
    }
}
