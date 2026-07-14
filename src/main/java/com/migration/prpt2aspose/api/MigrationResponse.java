package com.migration.prpt2aspose.api;

import com.migration.prpt2aspose.converter.ConversionResult;
import com.migration.prpt2aspose.model.ParsingWarning;
import com.migration.prpt2aspose.model.WarningSeverity;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** JSON body returned by POST /api/migrations: where the artifacts landed, plus everything needing human attention. */
public record MigrationResponse(
        String reportName,
        String outputDirectory,
        Map<String, String> artifacts,
        Map<String, String> downloadUrls,
        Map<WarningSeverity, Long> warningCounts,
        List<String> warnings) {

    public static MigrationResponse from(String slug, ConversionResult result) {
        List<ParsingWarning> allWarnings = result.allWarnings();
        String base = "/api/migrations/" + slug + "/";
        return new MigrationResponse(
                result.model().reportName(),
                result.generation().templateFile().getParent().toAbsolutePath().toString(),
                Map.of(
                        "template", result.generation().templateFile().toAbsolutePath().toString(),
                        "queries", result.queriesSqlFile().toAbsolutePath().toString(),
                        "mapping", result.mappingJsonFile().toAbsolutePath().toString(),
                        "migrationReport", result.migrationReportFile().toAbsolutePath().toString()),
                Map.of(
                        "template", base + "template.xlsx",
                        "queries", base + "queries.sql",
                        "mapping", base + "mapping.json",
                        "migrationReport", base + "migration-report.html"),
                allWarnings.stream()
                        .collect(Collectors.groupingBy(ParsingWarning::severity, Collectors.counting())),
                allWarnings.stream()
                        .map(w -> "[" + w.severity() + "] " + w.category() + " @ " + w.location() + ": " + w.message())
                        .toList());
    }
}
