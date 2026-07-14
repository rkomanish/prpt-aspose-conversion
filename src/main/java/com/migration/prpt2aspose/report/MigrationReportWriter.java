package com.migration.prpt2aspose.report;

import com.migration.prpt2aspose.generator.GenerationResult;
import com.migration.prpt2aspose.model.ParsingWarning;
import com.migration.prpt2aspose.model.ReportModel;
import com.migration.prpt2aspose.model.WarningSeverity;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Writes migration-report.html: conversion statistics, everything that was
 * converted cell-by-cell, and — most importantly — the warnings that call out
 * what needs manual attention, grouped by severity.
 */
@Component
public class MigrationReportWriter {

    public Path write(ReportModel model, GenerationResult generation, Path outputDir) throws IOException {
        Path file = outputDir.resolve("migration-report.html");

        List<ParsingWarning> allWarnings = new ArrayList<>(model.warnings());
        allWarnings.addAll(generation.warnings());
        Map<WarningSeverity, List<ParsingWarning>> bySeverity = allWarnings.stream()
                .collect(Collectors.groupingBy(ParsingWarning::severity));

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><meta charset='utf-8'><title>Migration Report – ")
                .append(escape(model.reportName())).append("</title><style>")
                .append("body{font-family:system-ui,sans-serif;margin:2rem;max-width:70rem}")
                .append("table{border-collapse:collapse;width:100%;margin:.5rem 0 1.5rem}")
                .append("th,td{border:1px solid #ccc;padding:.35rem .6rem;text-align:left;font-size:.9rem}")
                .append("th{background:#f0f0f0}")
                .append(".ERROR{background:#fde8e8}.WARNING{background:#fff6e0}.INFO{background:#eef6fd}")
                .append("code{background:#f5f5f5;padding:0 .25rem}")
                .append("</style></head><body>");

        html.append("<h1>Migration Report: ").append(escape(model.reportName())).append("</h1>");

        html.append("<h2>Conversion statistics</h2><table>");
        statRow(html, "Parameters", model.parameters().size());
        statRow(html, "Data sources", model.dataSources().size());
        statRow(html, "SQL queries", model.totalQueryCount());
        statRow(html, "Expressions", model.expressions().size());
        statRow(html, "Groups", model.groups().size());
        statRow(html, "Converted cells/items", generation.convertedItems().size());
        statRow(html, "Warnings (manual attention)", count(bySeverity, WarningSeverity.WARNING)
                + count(bySeverity, WarningSeverity.ERROR));
        statRow(html, "Informational notes", count(bySeverity, WarningSeverity.INFO));
        html.append("</table>");

        html.append("<h2>Manual fixes required / warnings</h2>");
        warningsTable(html, bySeverity.getOrDefault(WarningSeverity.ERROR, List.of()),
                bySeverity.getOrDefault(WarningSeverity.WARNING, List.of()));

        html.append("<h2>Converted items</h2><table><tr><th>#</th><th>Result</th></tr>");
        int i = 1;
        for (String item : generation.convertedItems()) {
            html.append("<tr><td>").append(i++).append("</td><td><code>")
                    .append(escape(item)).append("</code></td></tr>");
        }
        html.append("</table>");

        html.append("<h2>Notes</h2>");
        warningsTable(html, List.of(), bySeverity.getOrDefault(WarningSeverity.INFO, List.of()));

        html.append("</body></html>");
        Files.writeString(file, html.toString());
        return file;
    }

    private void warningsTable(StringBuilder html, List<ParsingWarning> errors, List<ParsingWarning> rest) {
        List<ParsingWarning> combined = new ArrayList<>(errors);
        combined.addAll(rest);
        if (combined.isEmpty()) {
            html.append("<p>None.</p>");
            return;
        }
        html.append("<table><tr><th>Severity</th><th>Category</th><th>Location</th><th>Detail</th></tr>");
        for (ParsingWarning w : combined) {
            html.append("<tr class='").append(w.severity()).append("'><td>").append(w.severity())
                    .append("</td><td>").append(escape(w.category()))
                    .append("</td><td><code>").append(escape(w.location()))
                    .append("</code></td><td>").append(escape(w.message())).append("</td></tr>");
        }
        html.append("</table>");
    }

    private void statRow(StringBuilder html, String label, long value) {
        html.append("<tr><td>").append(label).append("</td><td>").append(value).append("</td></tr>");
    }

    private long count(Map<WarningSeverity, List<ParsingWarning>> bySeverity, WarningSeverity severity) {
        return bySeverity.getOrDefault(severity, List.of()).size();
    }

    private String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
