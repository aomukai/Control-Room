package com.miniide.settings;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class SettingsService {
    private final Path settingsDir;
    private final Path securityPath;
    private final Path keysPath;
    private final Path vaultPath;
    private final ObjectMapper mapper;
    private final KeyVault keyVault;

    private SecuritySettings securitySettings;

    public SettingsService(Path settingsDir, ObjectMapper mapper) {
        this.settingsDir = settingsDir;
        this.securityPath = settingsDir.resolve("security.json");
        this.keysPath = settingsDir.resolve("agent-keys.json");
        this.vaultPath = settingsDir.resolve("agent-keys.vault");
        this.mapper = mapper;
        this.keyVault = new KeyVault();
        ensureSettingsDir();
        loadSecuritySettings();
    }

    public SecuritySettings getSecuritySettings() {
        return securitySettings;
    }

    public boolean isVaultUnlocked() {
        return keyVault.isUnlocked();
    }

    public void lockVault() {
        keyVault.lock();
    }

    public void unlockVault(String password) throws Exception {
        keyVault.unlock(vaultPath, password, mapper);
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
            migratePlaintextToEncrypted(password);
        } else if ("plaintext".equals(normalized)) {
            if (password == null || password.isBlank()) {
                throw new IllegalArgumentException("Password is required to disable encryption.");
            }
            migrateEncryptedToPlaintext(password);
        } else {
            throw new IllegalArgumentException("Unsupported security mode: " + mode);
        }

        securitySettings.setKeysSecurityMode(normalized);
        securitySettings.setUpdatedAt(System.currentTimeMillis());
        saveSecuritySettings();
        return securitySettings;
    }

    public AgentKeysMetadataFile listKeyMetadata() throws IOException {
        AgentKeysFile keys = loadKeysFile();
        return toMetadata(keys);
    }

    public String addKey(String provider, String label, String keyValue, String id, String password) throws Exception {
        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("Provider is required.");
        }
        if (keyValue == null || keyValue.isBlank()) {
            throw new IllegalArgumentException("API key is required.");
        }

        String normalizedProvider = provider.trim().toLowerCase(Locale.US);
        AgentKeysFile keys = loadKeysFile();
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
            keyVault.save(vaultPath, password, mapper, decrypted);
            writeMetadata(keys);
        } else {
            saveKeysFile(keys);
        }

        return normalizedProvider + ":" + keyId;
    }

    public void deleteKey(String provider, String id, String password) throws Exception {
        if (provider == null || provider.isBlank() || id == null || id.isBlank()) {
            throw new IllegalArgumentException("Provider and key id are required.");
        }
        String normalizedProvider = provider.trim().toLowerCase(Locale.US);
        AgentKeysFile keys = loadKeysFile();
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
                keyVault.save(vaultPath, password, mapper, decrypted);
            }
            writeMetadata(keys);
        } else {
            saveKeysFile(keys);
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
            AgentKeysFile decrypted = keyVault.getDecryptedKeys();
            return findKey(decrypted, provider, id);
        }

        AgentKeysFile keys = loadKeysFile();
        return findKey(keys, provider, id);
    }

    private void ensureSettingsDir() {
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

    private AgentKeysFile loadKeysFile() {
        if (!Files.exists(keysPath)) {
            return new AgentKeysFile();
        }
        try {
            AgentKeysFile file = mapper.readValue(keysPath.toFile(), AgentKeysFile.class);
            if (file.getProviders() == null) {
                file.setProviders(new HashMap<>());
            }
            return file;
        } catch (IOException e) {
            return new AgentKeysFile();
        }
    }

    private void saveKeysFile(AgentKeysFile keys) throws IOException {
        Files.createDirectories(keysPath.getParent());
        mapper.writerWithDefaultPrettyPrinter().writeValue(keysPath.toFile(), keys);
    }

    private void writeMetadata(AgentKeysFile keys) throws IOException {
        AgentKeysMetadataFile metadata = toMetadata(keys);
        Files.createDirectories(keysPath.getParent());
        mapper.writerWithDefaultPrettyPrinter().writeValue(keysPath.toFile(), metadata);
    }

    private AgentKeysMetadataFile toMetadata(AgentKeysFile keys) {
        AgentKeysMetadataFile metadata = new AgentKeysMetadataFile();
        metadata.setVersion(keys.getVersion());

        Map<String, List<AgentKeyMetadata>> providers = new HashMap<>();
        for (Map.Entry<String, List<AgentKeyRecord>> entry : keys.getProviders().entrySet()) {
            List<AgentKeyMetadata> items = new ArrayList<>();
            for (AgentKeyRecord record : entry.getValue()) {
                if (record != null) {
                    items.add(record.toMetadata());
                }
            }
            providers.put(entry.getKey(), items);
        }
        metadata.setProviders(providers);
        return metadata;
    }

    private void migratePlaintextToEncrypted(String password) throws Exception {
        AgentKeysFile keys = loadKeysFile();
        keyVault.save(vaultPath, password, mapper, keys);
        writeMetadata(keys);
    }

    private void migrateEncryptedToPlaintext(String password) throws Exception {
        AgentKeysFile keys = keyVault.unlock(vaultPath, password, mapper);
        saveKeysFile(keys);
        if (Files.exists(vaultPath)) {
            Files.delete(vaultPath);
        }
        keyVault.lock();
    }

    private void ensureUnlocked(String password) throws Exception {
        if (keyVault.isUnlocked()) {
            return;
        }
        if (password == null || password.isBlank()) {
            throw new IllegalStateException("Key vault is locked.");
        }
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

    private String findKey(AgentKeysFile keys, String provider, String id) {
        if (keys == null || keys.getProviders() == null) {
            return null;
        }
        List<AgentKeyRecord> entries = keys.getProviders().get(provider);
        if (entries == null) {
            return null;
        }
        for (AgentKeyRecord record : entries) {
            if (record != null && id.equals(record.getId())) {
                return record.getKey();
            }
        }
        return null;
    }
}
