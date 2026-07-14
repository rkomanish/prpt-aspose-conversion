package com.migration.prpt2aspose.parser;

import com.migration.prpt2aspose.model.ReportModel;

import java.io.InputStream;
import java.nio.file.Path;

/**
 * Inbound port: turns a .prpt file into the framework-agnostic {@link ReportModel}.
 * {@code converter}/{@code cli} depend on this interface, never on the concrete
 * {@link PrptDocumentParser} adapter.
 */
public interface PrptParser {

    ReportModel parse(Path prptFile);

    ReportModel parse(InputStream input, String sourceName);
}
