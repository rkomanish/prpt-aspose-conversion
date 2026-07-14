package com.migration.prpt2aspose.converter;

import java.nio.file.Path;
import java.util.List;

/** Outcome of one batch pass over a drop folder: what converted, what was already up to date, what failed and why. */
public record BatchResult(
        List<Converted> converted,
        List<Path> skippedUpToDate,
        List<Failed> failed) {

    public BatchResult {
        converted = List.copyOf(converted);
        skippedUpToDate = List.copyOf(skippedUpToDate);
        failed = List.copyOf(failed);
    }

    public record Converted(Path prptFile, ConversionResult result) {
    }

    public record Failed(Path prptFile, String error) {
    }

    public boolean didWork() {
        return !converted.isEmpty() || !failed.isEmpty();
    }
}
