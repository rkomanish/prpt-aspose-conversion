package com.migration.prpt2aspose.converter;

import com.migration.prpt2aspose.generator.GenerationResult;
import com.migration.prpt2aspose.model.ParsingWarning;
import com.migration.prpt2aspose.model.ReportModel;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Outcome of a full parse→generate→artifacts run: the model, generation details, and where each artifact landed. */
public record ConversionResult(
        ReportModel model,
        GenerationResult generation,
        Path queriesSqlFile,
        Path mappingJsonFile,
        Path migrationReportFile) {

    /** Parse-time and generation-time warnings combined, in that order. */
    public List<ParsingWarning> allWarnings() {
        List<ParsingWarning> all = new ArrayList<>(model.warnings());
        all.addAll(generation.warnings());
        return List.copyOf(all);
    }
}
