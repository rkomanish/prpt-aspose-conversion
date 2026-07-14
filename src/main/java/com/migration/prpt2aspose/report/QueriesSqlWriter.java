package com.migration.prpt2aspose.report;

import com.migration.prpt2aspose.model.ReportModel;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Writes queries.sql: every SQL query from every datasource, delimited and labeled by dataset name. */
@Component
public class QueriesSqlWriter {

    public Path write(ReportModel model, Path outputDir) throws IOException {
        Path file = outputDir.resolve("queries.sql");
        StringBuilder out = new StringBuilder();
        out.append("-- Extracted from PRPT report: ").append(model.reportName()).append("\n");
        model.parameters().forEach(p -> out.append("-- Parameter: ").append(p.name())
                .append(" (").append(p.type()).append(", default=").append(p.defaultValue()).append(")\n"));
        out.append("\n");
        model.dataSources().forEach(ds -> ds.queries().forEach(q -> {
            out.append("-- Dataset: ").append(q.name()).append("\n");
            out.append(q.sql().strip()).append(";\n\n");
        }));
        Files.writeString(file, out.toString());
        return file;
    }
}
