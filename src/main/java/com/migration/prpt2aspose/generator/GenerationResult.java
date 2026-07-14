package com.migration.prpt2aspose.generator;

import com.migration.prpt2aspose.model.ParsingWarning;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything a generation run produced: the template file, what was converted
 * (cell-by-cell, for migration-report.html), generation-time warnings (on top
 * of parse-time ones), and the dataset→suggested-Java-name mapping that
 * becomes mapping.json.
 */
public record GenerationResult(
        Path templateFile,
        List<String> convertedItems,
        List<ParsingWarning> warnings,
        Map<String, String> datasetMapping) {

    public GenerationResult {
        convertedItems = List.copyOf(convertedItems);
        warnings = List.copyOf(warnings);
        // not Map.copyOf: dataset order (primary query first) must survive into mapping.json
        datasetMapping = Collections.unmodifiableMap(new LinkedHashMap<>(datasetMapping));
    }
}
