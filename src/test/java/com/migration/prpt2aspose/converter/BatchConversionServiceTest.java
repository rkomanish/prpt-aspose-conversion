package com.migration.prpt2aspose.converter;

import com.migration.prpt2aspose.generator.AsposeSmartMarkerGenerator;
import com.migration.prpt2aspose.mapping.MappingJsonWriter;
import com.migration.prpt2aspose.parser.PrptDocumentParser;
import com.migration.prpt2aspose.report.MigrationReportWriter;
import com.migration.prpt2aspose.report.QueriesSqlWriter;
import com.migration.prpt2aspose.styles.AsposeStyleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The drop-folder contract: every .prpt in the folder converts into its own
 * output subfolder with template + queries, a broken file doesn't take down
 * the batch, and a second pass over unchanged inputs is a no-op.
 */
class BatchConversionServiceTest {

    @TempDir
    Path inputDir;

    @TempDir
    Path outputDir;

    private BatchConversionService batch;

    @BeforeEach
    void setUp() throws IOException {
        Files.copy(Path.of("src/test/resources/samples/customer-orders.prpt"),
                inputDir.resolve("customer-orders.prpt"), StandardCopyOption.REPLACE_EXISTING);
        Files.copy(Path.of("src/test/resources/samples/product-list.prpt"),
                inputDir.resolve("product-list.prpt"), StandardCopyOption.REPLACE_EXISTING);
        Files.writeString(inputDir.resolve("broken.prpt"), "this is not a zip archive");

        batch = new BatchConversionService(new ConversionService(
                new PrptDocumentParser(),
                new AsposeSmartMarkerGenerator(new AsposeStyleMapper()),
                new QueriesSqlWriter(),
                new MappingJsonWriter(),
                new MigrationReportWriter()));
    }

    @Test
    void convertsEveryPrptIntoItsOwnFolderAndIsolatesFailures() {
        BatchResult result = batch.convertAll(inputDir, outputDir);

        assertThat(result.converted()).hasSize(2);
        assertThat(result.failed()).hasSize(1);
        assertThat(result.failed().get(0).prptFile().getFileName().toString()).isEqualTo("broken.prpt");

        for (String report : new String[]{"customer-orders", "product-list"}) {
            Path dir = outputDir.resolve(report);
            assertThat(dir.resolve("template.xlsx")).exists();
            assertThat(dir.resolve("queries.sql")).exists();
            assertThat(dir.resolve("mapping.json")).exists();
            assertThat(dir.resolve("migration-report.html")).exists();
        }
    }

    @Test
    void secondPassSkipsUpToDateOutputs() {
        batch.convertAll(inputDir, outputDir);
        BatchResult second = batch.convertAll(inputDir, outputDir);

        assertThat(second.converted()).isEmpty();
        assertThat(second.skippedUpToDate()).hasSize(2);
        // the broken file is retried each pass (it never produced output)
        assertThat(second.failed()).hasSize(1);
    }

    @Test
    void touchedPrptIsReconverted() throws IOException {
        batch.convertAll(inputDir, outputDir);
        Path prpt = inputDir.resolve("product-list.prpt");
        Files.setLastModifiedTime(prpt, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() + 5_000));

        BatchResult second = batch.convertAll(inputDir, outputDir);

        assertThat(second.converted())
                .extracting(c -> c.prptFile().getFileName().toString())
                .containsExactly("product-list.prpt");
    }
}
