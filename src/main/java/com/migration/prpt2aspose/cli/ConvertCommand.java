package com.migration.prpt2aspose.cli;

import com.migration.prpt2aspose.converter.BatchConversionService;
import com.migration.prpt2aspose.converter.BatchResult;
import com.migration.prpt2aspose.converter.ConversionService;
import com.migration.prpt2aspose.converter.ConversionSummary;
import com.migration.prpt2aspose.model.ReportModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Duration;

/**
 * CLI surface, three modes:
 *
 * <pre>
 *   --input=<file.prpt> [--output=<dir>]                        single file (no --output: parse + summary only)
 *   --input-dir=<dir> [--output-dir=<dir>]                      batch: convert every .prpt in the folder
 *   --input-dir=<dir> [--output-dir=<dir>] --watch [--poll-seconds=5]
 *                                                               drop-folder mode: keep watching for new .prpt files
 * </pre>
 *
 * {@code --output-dir} defaults to {@code <input-dir>/converted}. Each report
 * lands in its own subfolder: template.xlsx + queries.sql + mapping.json +
 * migration-report.html.
 */
@Component
public class ConvertCommand implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ConvertCommand.class);

    private final ConversionService conversionService;
    private final BatchConversionService batchConversionService;
    private final com.migration.prpt2aspose.parser.PrptInspector inspector;

    public ConvertCommand(ConversionService conversionService,
                          BatchConversionService batchConversionService,
                          com.migration.prpt2aspose.parser.PrptInspector inspector) {
        this.conversionService = conversionService;
        this.batchConversionService = batchConversionService;
        this.inspector = inspector;
    }

    @Override
    public void run(ApplicationArguments args) throws InterruptedException {
        if (args.containsOption("input-dir") && !args.getOptionValues("input-dir").isEmpty()) {
            runFolderMode(args);
            return;
        }
        if (args.containsOption("input") && !args.getOptionValues("input").isEmpty()) {
            runSingleFileMode(args);
            return;
        }
        log.info("No CLI arguments given — running as HTTP service only.");
        log.info("  POST http://localhost:8080/api/migrations   (multipart field 'file' = your .prpt)");
        log.info("  GET  http://localhost:8080/api/migrations/{report}/template.xlsx | queries.sql | mapping.json | migration-report.html");
        log.info("CLI usage (add as program arguments):");
        log.info("  --input=<file.prpt> [--output=<dir>] [--inspect]");
        log.info("  --input-dir=<dir> [--output-dir=<dir>] [--watch] [--poll-seconds=5]");
        log.info("  (for CLI-only runs without the HTTP server: --spring.main.web-application-type=none)");
    }

    private void runFolderMode(ApplicationArguments args) throws InterruptedException {
        Path inputDir = Path.of(args.getOptionValues("input-dir").get(0));
        Path outputDir = args.containsOption("output-dir") && !args.getOptionValues("output-dir").isEmpty()
                ? Path.of(args.getOptionValues("output-dir").get(0))
                : inputDir.resolve("converted");

        if (args.containsOption("watch")) {
            long pollSeconds = args.containsOption("poll-seconds") && !args.getOptionValues("poll-seconds").isEmpty()
                    ? Long.parseLong(args.getOptionValues("poll-seconds").get(0))
                    : 5;
            batchConversionService.watch(inputDir, outputDir, Duration.ofSeconds(pollSeconds));
            return;
        }

        BatchResult result = batchConversionService.convertAll(inputDir, outputDir);
        result.converted().forEach(c -> log.info("OK      {} -> {}",
                c.prptFile().getFileName(), c.result().generation().templateFile()));
        result.skippedUpToDate().forEach(p -> log.info("SKIP    {} (output up to date)", p.getFileName()));
        result.failed().forEach(f -> log.error("FAILED  {} : {}", f.prptFile().getFileName(), f.error()));
        log.info("Batch complete: {} converted, {} skipped, {} failed — outputs in {}",
                result.converted().size(), result.skippedUpToDate().size(), result.failed().size(),
                outputDir.toAbsolutePath());
    }

    private void runSingleFileMode(ApplicationArguments args) {
        Path prptFile = Path.of(args.getOptionValues("input").get(0));

        if (args.containsOption("inspect")) {
            System.out.println(inspector.inspect(prptFile));
            return;
        }
        log.info("Parsing {}", prptFile.toAbsolutePath());

        if (args.containsOption("output") && !args.getOptionValues("output").isEmpty()) {
            Path outputDir = Path.of(args.getOptionValues("output").get(0));
            var result = conversionService.convert(prptFile, outputDir);
            logSummary(ConversionSummary.from(result.model()));
            log.info("Template:         {}", result.generation().templateFile());
            log.info("Queries:          {}", result.queriesSqlFile());
            log.info("Mapping:          {}", result.mappingJsonFile());
            log.info("Migration report: {}", result.migrationReportFile());
            return;
        }

        ReportModel model = conversionService.parse(prptFile);
        logSummary(ConversionSummary.from(model));
    }

    private void logSummary(ConversionSummary summary) {
        log.info("Report name: {}", summary.reportName());
        log.info("Parameters: {}", summary.parameterCount());
        log.info("Data sources: {} (total queries: {})", summary.dataSourceCount(), summary.queryCount());
        log.info("Expressions: {}", summary.expressionCount());
        log.info("Groups (including nested): {}", summary.groupCount());
        log.info("Warnings by severity: {}", summary.warningCountsBySeverity());
        summary.warningLines().forEach(log::warn);
    }
}
