package com.migration.prpt2aspose.converter;

import com.migration.prpt2aspose.generator.AsposeSmartMarkerGenerator;
import com.migration.prpt2aspose.mapping.MappingJsonWriter;
import com.migration.prpt2aspose.model.ReportModel;
import com.migration.prpt2aspose.parser.PrptDocumentParser;
import com.migration.prpt2aspose.report.MigrationReportWriter;
import com.migration.prpt2aspose.report.QueriesSqlWriter;
import com.migration.prpt2aspose.styles.AsposeStyleMapper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Activates automatically once real .prpt file(s) are dropped into
 * src/test/resources/samples/ — no code changes needed. Until then it's
 * skipped (not failed) via an assumption, since Phase 1 doesn't depend on a
 * real sample being available.
 */
class RealSampleIntegrationTest {

    private static final Path SAMPLES_DIR = Path.of("src/test/resources/samples");

    @Test
    void parsesEveryRealSampleFoundOnDisk() throws IOException {
        List<Path> samples = findSamples();
        Assumptions.assumeFalse(samples.isEmpty(),
                "No real .prpt sample found in " + SAMPLES_DIR + " — skipping until one is provided.");

        ConversionService conversionService = new ConversionService(
                new PrptDocumentParser(),
                new AsposeSmartMarkerGenerator(new AsposeStyleMapper()),
                new QueriesSqlWriter(),
                new MappingJsonWriter(),
                new MigrationReportWriter());

        for (Path sample : samples) {
            Path outputDir = Path.of("target/test-output",
                    sample.getFileName().toString().replace(".prpt", ""));
            ConversionResult result = conversionService.convert(sample, outputDir);
            ReportModel model = result.model();
            ConversionSummary summary = ConversionSummary.from(model);

            assertThat(result.generation().templateFile()).exists();
            assertThat(result.queriesSqlFile()).exists();
            assertThat(result.mappingJsonFile()).exists();
            assertThat(result.migrationReportFile()).exists();

            System.out.println("=== " + sample.getFileName() + " ===");
            System.out.println("Report name: " + summary.reportName());
            System.out.println("Parameters: " + summary.parameterCount());
            System.out.println("Data sources: " + summary.dataSourceCount() + " / queries: " + summary.queryCount());
            System.out.println("Expressions: " + summary.expressionCount());
            System.out.println("Groups: " + summary.groupCount());
            System.out.println("Warnings: " + summary.warningCountsBySeverity());
            summary.warningLines().forEach(line -> System.out.println("  " + line));

            assertThat(model.reportName()).isNotBlank();
        }
    }

    private List<Path> findSamples() throws IOException {
        if (!Files.exists(SAMPLES_DIR)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(SAMPLES_DIR)) {
            return stream.filter(p -> p.toString().endsWith(".prpt")).toList();
        }
    }
}
