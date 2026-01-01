package com.miniide.settings;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles plaintext key file storage and metadata operations.
 */
public class PlaintextKeyStore {
    private final Path keysPath;
    private final ObjectMapper mapper;

    public PlaintextKeyStore(Path keysPath, ObjectMapper mapper) {
        this.keysPath = keysPath;
        this.mapper = mapper;
    }

    public AgentKeysFile load() {
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

    public void save(AgentKeysFile keys) throws IOException {
        Files.createDirectories(keysPath.getParent());
        mapper.writerWithDefaultPrettyPrinter().writeValue(keysPath.toFile(), keys);
    }

    public void saveMetadataOnly(AgentKeysFile keys) throws IOException {
        AgentKeysMetadataFile metadata = toMetadata(keys);
        Files.createDirectories(keysPath.getParent());
        mapper.writerWithDefaultPrettyPrinter().writeValue(keysPath.toFile(), metadata);
    }

    public AgentKeysMetadataFile toMetadata(AgentKeysFile keys) {
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

    public String findKey(AgentKeysFile keys, String provider, String id) {
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
