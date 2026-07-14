package com.migration.prpt2aspose.mapping;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Writes mapping.json: dataset (Smart Marker prefix / query name) → suggested
 * Java-service data source name. Hand-rolled serialization on purpose — the
 * payload is a flat string map and this keeps the module free of a JSON
 * library dependency.
 */
@Component
public class MappingJsonWriter {

    public Path write(Map<String, String> datasetMapping, Path outputDir) throws IOException {
        Path file = outputDir.resolve("mapping.json");
        String body = datasetMapping.entrySet().stream()
                .map(e -> "  \"" + escape(e.getKey()) + "\": \"" + escape(e.getValue()) + "\"")
                .collect(Collectors.joining(",\n", "{\n", "\n}\n"));
        Files.writeString(file, body);
        return file;
    }

    private String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
