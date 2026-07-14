package com.migration.prpt2aspose.converter;

import com.migration.prpt2aspose.generator.GenerationResult;
import com.migration.prpt2aspose.generator.TemplateGenerator;
import com.migration.prpt2aspose.mapping.MappingJsonWriter;
import com.migration.prpt2aspose.model.ReportModel;
import com.migration.prpt2aspose.parser.PrptParser;
import com.migration.prpt2aspose.report.MigrationReportWriter;
import com.migration.prpt2aspose.report.QueriesSqlWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

/**
 * Application-layer orchestrator: parse → generate template → write artifacts
 * (queries.sql, mapping.json, migration-report.html). Depends only on ports
 * and writers, never on a concrete parser/generator implementation.
 */
@Service
public class ConversionService {

    private static final Logger log = LoggerFactory.getLogger(ConversionService.class);

    private final PrptParser prptParser;
    private final TemplateGenerator templateGenerator;
    private final QueriesSqlWriter queriesSqlWriter;
    private final MappingJsonWriter mappingJsonWriter;
    private final MigrationReportWriter migrationReportWriter;

    public ConversionService(PrptParser prptParser,
                             TemplateGenerator templateGenerator,
                             QueriesSqlWriter queriesSqlWriter,
                             MappingJsonWriter mappingJsonWriter,
                             MigrationReportWriter migrationReportWriter) {
        this.prptParser = prptParser;
        this.templateGenerator = templateGenerator;
        this.queriesSqlWriter = queriesSqlWriter;
        this.mappingJsonWriter = mappingJsonWriter;
        this.migrationReportWriter = migrationReportWriter;
    }

    public ReportModel parse(Path prptFile) {
        return prptParser.parse(prptFile);
    }

    public ConversionResult convert(Path prptFile, Path outputDir) {
        ReportModel model = parse(prptFile);
        try {
            GenerationResult generation = templateGenerator.generate(model, outputDir);
            Path queriesSql = queriesSqlWriter.write(model, outputDir);
            Path mappingJson = mappingJsonWriter.write(generation.datasetMapping(), outputDir);
            Path migrationReport = migrationReportWriter.write(model, generation, outputDir);
            extractEmbeddedResources(prptFile, outputDir);

            log.info("Conversion of '{}' complete → {}", model.reportName(), outputDir.toAbsolutePath());
            return new ConversionResult(model, generation, queriesSql, mappingJson, migrationReport);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed writing conversion outputs to " + outputDir, e);
        }
    }

    /**
     * Copies binary payloads shipped inside the .prpt (logos, images, embedded
     * Excel templates) to {@code <outputDir>/resources/} — these often ARE the
     * visual identity of the legacy report, so losing them would mean manual
     * rework later. Failure here never fails the conversion.
     */
    private void extractEmbeddedResources(Path prptFile, Path outputDir) {
        try {
            var resources = com.migration.prpt2aspose.parser.PrptArchiveReader.open(prptFile).resourceEntries();
            if (resources.isEmpty()) {
                return;
            }
            Path resourcesDir = outputDir.resolve("resources");
            java.nio.file.Files.createDirectories(resourcesDir);
            for (var entry : resources.entrySet()) {
                // flatten to the bare filename: archive paths must not escape the output dir
                String fileName = Path.of(entry.getKey()).getFileName().toString();
                java.nio.file.Files.write(resourcesDir.resolve(fileName), entry.getValue());
                log.info("Extracted embedded resource {} ({} bytes) -> {}",
                        entry.getKey(), entry.getValue().length, resourcesDir.resolve(fileName));
            }
        } catch (IOException e) {
            log.warn("Could not extract embedded resources from {}: {}", prptFile.getFileName(), e.getMessage());
        }
    }
}
