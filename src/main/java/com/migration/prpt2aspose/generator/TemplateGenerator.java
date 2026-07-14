package com.migration.prpt2aspose.generator;

import com.migration.prpt2aspose.model.ReportModel;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Output-side port of the hexagon. {@link com.migration.prpt2aspose.generator.AsposeSmartMarkerGenerator}
 * is the first adapter; a Jasper/HTML/PDF generator plugs in here later
 * without touching the parser or the model.
 */
public interface TemplateGenerator {

    /** Target format name, used for logging and the migration report. */
    String targetName();

    GenerationResult generate(ReportModel model, Path outputDir) throws IOException;
}
