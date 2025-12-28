package com.miniide.storage;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class JsonStorage {

    private static final ObjectMapper mapper = new ObjectMapper();

    public static <T> List<T> readJsonList(String path, Class<T[]> clazz) throws IOException {
        Path filePath = Paths.get(path);
        if (!Files.exists(filePath)) {
            return Collections.emptyList();
        }
        T[] items = mapper.readValue(filePath.toFile(), clazz);
        if (items == null || items.length == 0) {
            return Collections.emptyList();
        }
        return Arrays.asList(items);
    }

    public static void writeJsonList(String path, List<?> data) throws IOException {
        Path filePath = Paths.get(path);
        Path parent = filePath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        mapper.writerWithDefaultPrettyPrinter().writeValue(filePath.toFile(), data);
    }
}
