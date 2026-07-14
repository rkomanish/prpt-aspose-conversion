package com.migration.prpt2aspose.api;

import com.migration.prpt2aspose.converter.ConversionResult;
import com.migration.prpt2aspose.converter.ConversionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Set;

/**
 * HTTP surface for interactive testing (IntelliJ/Postman/curl):
 *
 * <pre>
 *   POST /api/migrations            multipart field "file" = the .prpt  ->  JSON summary + download URLs
 *   GET  /api/migrations/{report}/template.xlsx                         ->  the Smart Marker template
 *   GET  /api/migrations/{report}/queries.sql                           ->  extracted SQL
 *   GET  /api/migrations/{report}/mapping.json
 *   GET  /api/migrations/{report}/migration-report.html
 * </pre>
 *
 * Outputs land in {@code prpt2aspose.output-dir} (default ./output), the same
 * layout the CLI batch mode produces, so both entry points stay interchangeable.
 */
@RestController
@RequestMapping("/api/migrations")
public class MigrationController {

    private static final Logger log = LoggerFactory.getLogger(MigrationController.class);

    private static final Set<String> DOWNLOADABLE_ARTIFACTS =
            Set.of("template.xlsx", "queries.sql", "mapping.json", "migration-report.html");

    private final ConversionService conversionService;
    private final Path outputRoot;

    public MigrationController(
            ConversionService conversionService,
            @Value("${prpt2aspose.output-dir:output}") String outputDir) {
        this.conversionService = conversionService;
        this.outputRoot = Path.of(outputDir);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MigrationResponse> migrate(@RequestParam("file") MultipartFile file) throws IOException {
        String originalName = file.getOriginalFilename();
        if (originalName == null || !originalName.toLowerCase().endsWith(".prpt")) {
            throw new IllegalArgumentException("Upload a .prpt file in the multipart field 'file'.");
        }

        String slug = slugOf(originalName);
        Path uploadedCopy = Files.createTempFile("upload-", "-" + slug + ".prpt");
        try {
            file.transferTo(uploadedCopy);
            ConversionResult result = conversionService.convert(uploadedCopy, outputRoot.resolve(slug));
            log.info("HTTP migration of {} -> {}", originalName, outputRoot.resolve(slug).toAbsolutePath());
            return ResponseEntity.ok(MigrationResponse.from(slug, result));
        } finally {
            Files.deleteIfExists(uploadedCopy);
        }
    }

    @GetMapping("/{report}/{artifact}")
    public ResponseEntity<FileSystemResource> download(
            @PathVariable String report, @PathVariable String artifact) {
        if (!DOWNLOADABLE_ARTIFACTS.contains(artifact)) {
            return ResponseEntity.notFound().build();
        }
        Path file = outputRoot.resolve(slugOf(report)).resolve(artifact).normalize();
        if (!file.startsWith(outputRoot) && !file.isAbsolute()) {
            return ResponseEntity.notFound().build();
        }
        if (!Files.isRegularFile(file)) {
            return ResponseEntity.notFound().build();
        }
        MediaType contentType = switch (artifact) {
            case "template.xlsx" -> MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            case "mapping.json" -> MediaType.APPLICATION_JSON;
            case "migration-report.html" -> MediaType.TEXT_HTML;
            default -> MediaType.TEXT_PLAIN;
        };
        return ResponseEntity.ok()
                .contentType(contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + artifact + "\"")
                .body(new FileSystemResource(file));
    }

    @ExceptionHandler({IllegalArgumentException.class, RuntimeException.class})
    public ResponseEntity<Map<String, String>> onError(Exception e) {
        HttpStatus status = e instanceof IllegalArgumentException ? HttpStatus.BAD_REQUEST : HttpStatus.UNPROCESSABLE_ENTITY;
        return ResponseEntity.status(status).body(Map.of("error", String.valueOf(e.getMessage())));
    }

    /** Keeps the report name filesystem- and URL-safe; also blocks path traversal via crafted filenames. */
    private static String slugOf(String fileName) {
        String base = fileName.replaceAll("(?i)\\.prpt$", "");
        int lastSlash = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));
        base = base.substring(lastSlash + 1);
        String slug = base.replaceAll("[^A-Za-z0-9._-]", "_");
        if (slug.isBlank() || slug.startsWith(".")) {
            throw new IllegalArgumentException("Invalid report file name: " + fileName);
        }
        return slug;
    }
}
