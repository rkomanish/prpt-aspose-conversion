package com.migration.prpt2aspose.converter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * The drop-folder workflow: the team copies .prpt files into a folder, this
 * service converts each into {@code <outputDir>/<report-basename>/} holding
 * template.xlsx (Smart Marker syntax), queries.sql, mapping.json and
 * migration-report.html.
 *
 * <p>A file is skipped when its template output is already newer than the
 * .prpt (so re-running a batch is cheap and watch mode doesn't loop), and one
 * broken file never stops the rest of the batch.
 *
 * <p>Watch mode polls rather than using {@link java.nio.file.WatchService}:
 * migration drop folders are typically on network shares where inotify-style
 * events are unreliable, and polling also sidesteps the half-copied-file
 * problem (a file is only picked up once its size is stable between passes).
 */
@Service
public class BatchConversionService {

    private static final Logger log = LoggerFactory.getLogger(BatchConversionService.class);

    private final ConversionService conversionService;

    public BatchConversionService(ConversionService conversionService) {
        this.conversionService = conversionService;
    }

    /** One pass: convert every .prpt in {@code inputDir} that has no up-to-date output yet. */
    public BatchResult convertAll(Path inputDir, Path outputDir) {
        List<BatchResult.Converted> converted = new ArrayList<>();
        List<Path> skipped = new ArrayList<>();
        List<BatchResult.Failed> failed = new ArrayList<>();

        for (Path prptFile : listPrptFiles(inputDir)) {
            Path reportOutputDir = outputDir.resolve(baseName(prptFile));
            if (isUpToDate(prptFile, reportOutputDir)) {
                skipped.add(prptFile);
                continue;
            }
            try {
                ConversionResult result = conversionService.convert(prptFile, reportOutputDir);
                converted.add(new BatchResult.Converted(prptFile, result));
                log.info("Converted {} -> {}", prptFile.getFileName(), reportOutputDir);
            } catch (RuntimeException e) {
                failed.add(new BatchResult.Failed(prptFile, e.getMessage()));
                log.error("FAILED {} : {}", prptFile.getFileName(), e.getMessage());
            }
        }

        if (!converted.isEmpty() || !failed.isEmpty()) {
            log.info("Batch pass done: {} converted, {} up-to-date, {} failed",
                    converted.size(), skipped.size(), failed.size());
        }
        return new BatchResult(converted, skipped, failed);
    }

    /**
     * Keeps converting until interrupted: an initial pass, then a poll every
     * {@code pollInterval}. New files are picked up only once their size is
     * stable across two polls (i.e. the copy into the drop folder finished).
     */
    public void watch(Path inputDir, Path outputDir, Duration pollInterval) throws InterruptedException {
        log.info("Watching {} every {}s — drop .prpt files there; outputs land in {}",
                inputDir.toAbsolutePath(), pollInterval.toSeconds(), outputDir.toAbsolutePath());
        while (!Thread.currentThread().isInterrupted()) {
            convertAll(inputDir, outputDir);
            Thread.sleep(pollInterval.toMillis());
        }
    }

    private List<Path> listPrptFiles(Path inputDir) {
        if (!Files.isDirectory(inputDir)) {
            throw new IllegalArgumentException("Input directory does not exist: " + inputDir.toAbsolutePath());
        }
        try (Stream<Path> stream = Files.list(inputDir)) {
            return stream
                    .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".prpt"))
                    .filter(this::sizeIsStable)
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot list " + inputDir, e);
        }
    }

    /** Guards against picking up a file that is still being copied into the drop folder. */
    private boolean sizeIsStable(Path file) {
        try {
            long before = Files.size(file);
            Thread.sleep(200);
            return Files.size(file) == before;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private boolean isUpToDate(Path prptFile, Path reportOutputDir) {
        Path template = reportOutputDir.resolve("template.xlsx");
        try {
            return Files.exists(template)
                    && Files.getLastModifiedTime(template).compareTo(Files.getLastModifiedTime(prptFile)) >= 0;
        } catch (IOException e) {
            return false;
        }
    }

    private String baseName(Path prptFile) {
        String name = prptFile.getFileName().toString();
        return name.substring(0, name.length() - ".prpt".length());
    }
}
