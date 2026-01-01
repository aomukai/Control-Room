package com.miniide.settings;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Service for managing security settings and API keys.
 * Delegates to PlaintextKeyStore for file I/O and KeyMigrationService for mode switching.
 */
public class SettingsService {
    private final Path securityPath;
    private final ObjectMapper mapper;
    private final KeyVault keyVault;
    private final PlaintextKeyStore plaintextStore;
    private final KeyMigrationService migrationService;

    private SecuritySettings securitySettings;

    public SettingsService(Path settingsDir, ObjectMapper mapper) {
        this.securityPath = settingsDir.resolve("security.json");
        Path keysPath = settingsDir.resolve("agent-keys.json");
        Path vaultPath = settingsDir.resolve("agent-keys.vault");
        this.mapper = mapper;
        this.keyVault = new KeyVault();
        this.plaintextStore = new PlaintextKeyStore(keysPath, mapper);
        this.migrationService = new KeyMigrationService(plaintextStore, keyVault, vaultPath, mapper);

        ensureSettingsDir(settingsDir);
        loadSecuritySettings();
    }

    // ===== Security Settings =====

    public SecuritySettings getSecuritySettings() {
        return securitySettings;
    }

    public SecuritySettings updateSecurityMode(String mode, String password) throws Exception {
        String normalized = normalizeMode(mode);
        String current = normalizeMode(securitySettings.getKeysSecurityMode());

        if (normalized.equals(current)) {
            return securitySettings;
        }

        if ("encrypted".equals(normalized)) {
            if (password == null || password.isBlank()) {
                throw new IllegalArgumentException("Password is required to enable encryption.");
            }
            migrationService.migrateToEncrypted(password);
        } else if ("plaintext".equals(normalized)) {
            if (password == null || password.isBlank()) {
                throw new IllegalArgumentException("Password is required to disable encryption.");
            }
            migrationService.migrateToPlaintext(password);
        } else {
            throw new IllegalArgumentException("Unsupported security mode: " + mode);
        }

        securitySettings.setKeysSecurityMode(normalized);
        securitySettings.setUpdatedAt(System.currentTimeMillis());
        saveSecuritySettings();
        return securitySettings;
    }

    // ===== Vault Operations =====

    public boolean isVaultUnlocked() {
        return keyVault.isUnlocked();
    }

    public void lockVault() {
        keyVault.lock();
    }

    public void unlockVault(String password) throws Exception {
        Path vaultPath = securityPath.getParent().resolve("agent-keys.vault");
        keyVault.unlock(vaultPath, password, mapper);
    }

    // ===== Key Management =====

    public AgentKeysMetadataFile listKeyMetadata() throws IOException {
        AgentKeysFile keys = plaintextStore.load();
        return plaintextStore.toMetadata(keys);
    }

    public String addKey(String provider, String label, String keyValue, String id, String password) throws Exception {
        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("Provider is required.");
        }
        if (keyValue == null || keyValue.isBlank()) {
            throw new IllegalArgumentException("API key is required.");
        }

        String normalizedProvider = provider.trim().toLowerCase(Locale.US);
        AgentKeysFile keys = plaintextStore.load();
        List<AgentKeyRecord> entries = keys.getProviders().computeIfAbsent(normalizedProvider, _k -> new ArrayList<>());

        String keyId = (id == null || id.isBlank()) ? generateKeyId(normalizedProvider) : id.trim();
        AgentKeyRecord record = new AgentKeyRecord();
        record.setId(keyId);
        record.setLabel(label == null || label.isBlank() ? "Key " + keyId : label.trim());
        record.setKey(keyValue.trim());
        record.setCreatedAt(System.currentTimeMillis());

        entries.add(record);

        if (isEncryptedMode()) {
            ensureUnlocked(password);
            AgentKeysFile decrypted = keyVault.getDecryptedKeys();
            if (decrypted == null) {
                decrypted = new AgentKeysFile();
            }
            List<AgentKeyRecord> decryptedEntries = decrypted.getProviders()
                .computeIfAbsent(normalizedProvider, _k -> new ArrayList<>());
            decryptedEntries.add(record);
            Path vaultPath = securityPath.getParent().resolve("agent-keys.vault");
            keyVault.save(vaultPath, password, mapper, decrypted);
            plaintextStore.saveMetadataOnly(keys);
        } else {
            plaintextStore.save(keys);
        }

        return normalizedProvider + ":" + keyId;
    }

    public void deleteKey(String provider, String id, String password) throws Exception {
        if (provider == null || provider.isBlank() || id == null || id.isBlank()) {
            throw new IllegalArgumentException("Provider and key id are required.");
        }
        String normalizedProvider = provider.trim().toLowerCase(Locale.US);
        AgentKeysFile keys = plaintextStore.load();
        List<AgentKeyRecord> entries = keys.getProviders().get(normalizedProvider);
        if (entries != null) {
            entries.removeIf(entry -> id.equals(entry.getId()));
        }

        if (isEncryptedMode()) {
            ensureUnlocked(password);
            AgentKeysFile decrypted = keyVault.getDecryptedKeys();
            if (decrypted != null && decrypted.getProviders().containsKey(normalizedProvider)) {
                decrypted.getProviders().get(normalizedProvider)
                    .removeIf(entry -> id.equals(entry.getId()));
                Path vaultPath = securityPath.getParent().resolve("agent-keys.vault");
                keyVault.save(vaultPath, password, mapper, decrypted);
            }
            plaintextStore.saveMetadataOnly(keys);
        } else {
            plaintextStore.save(keys);
        }
    }

    public String resolveKey(String keyRef) throws Exception {
        if (keyRef == null || keyRef.isBlank()) {
            return null;
        }
        String[] parts = keyRef.split(":", 2);
        if (parts.length != 2) {
            return null;
        }
        String provider = parts[0].toLowerCase(Locale.US);
        String id = parts[1];

        if (isEncryptedMode()) {
            if (!keyVault.isUnlocked()) {
                throw new IllegalStateException("Key vault is locked.");
            }
            return plaintextStore.findKey(keyVault.getDecryptedKeys(), provider, id);
        }

        return plaintextStore.findKey(plaintextStore.load(), provider, id);
    }

    // ===== Private Helpers =====

    private void ensureSettingsDir(Path settingsDir) {
        try {
            Files.createDirectories(settingsDir);
        } catch (IOException ignored) {
        }
    }

    private void loadSecuritySettings() {
        if (Files.exists(securityPath)) {
            try {
                securitySettings = mapper.readValue(securityPath.toFile(), SecuritySettings.class);
                if (securitySettings.getKeysSecurityMode() == null) {
                    securitySettings.setKeysSecurityMode("plaintext");
                }
                return;
            } catch (IOException ignored) {
            }
        }
        securitySettings = new SecuritySettings();
        saveSecuritySettings();
    }

    private void saveSecuritySettings() {
        try {
            Files.createDirectories(securityPath.getParent());
            mapper.writerWithDefaultPrettyPrinter().writeValue(securityPath.toFile(), securitySettings);
        } catch (IOException ignored) {
        }
    }

    private void ensureUnlocked(String password) throws Exception {
        if (keyVault.isUnlocked()) {
            return;
        }
        if (password == null || password.isBlank()) {
            throw new IllegalStateException("Key vault is locked.");
        }
        Path vaultPath = securityPath.getParent().resolve("agent-keys.vault");
        keyVault.unlock(vaultPath, password, mapper);
    }

    private boolean isEncryptedMode() {
        return "encrypted".equalsIgnoreCase(securitySettings.getKeysSecurityMode());
    }

    private String normalizeMode(String mode) {
        if (mode == null) {
            return "plaintext";
        }
        return mode.trim().toLowerCase(Locale.US);
    }

    private String generateKeyId(String provider) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        return provider + "-" + suffix;
    }
}
