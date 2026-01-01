package com.miniide.settings;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Handles migration between plaintext and encrypted key storage modes.
 */
public class KeyMigrationService {
    private final PlaintextKeyStore plaintextStore;
    private final KeyVault keyVault;
    private final Path vaultPath;
    private final ObjectMapper mapper;

    public KeyMigrationService(PlaintextKeyStore plaintextStore, KeyVault keyVault,
                               Path vaultPath, ObjectMapper mapper) {
        this.plaintextStore = plaintextStore;
        this.keyVault = keyVault;
        this.vaultPath = vaultPath;
        this.mapper = mapper;
    }

    /**
     * Migrates keys from plaintext storage to encrypted vault.
     * After migration, plaintext file contains only metadata (no actual keys).
     */
    public void migrateToEncrypted(String password) throws Exception {
        AgentKeysFile keys = plaintextStore.load();
        keyVault.save(vaultPath, password, mapper, keys);
        plaintextStore.saveMetadataOnly(keys);
    }

    /**
     * Migrates keys from encrypted vault to plaintext storage.
     * After migration, vault file is deleted.
     */
    public void migrateToPlaintext(String password) throws Exception {
        AgentKeysFile keys = keyVault.unlock(vaultPath, password, mapper);
        plaintextStore.save(keys);
        if (Files.exists(vaultPath)) {
            Files.delete(vaultPath);
        }
        keyVault.lock();
    }
}
