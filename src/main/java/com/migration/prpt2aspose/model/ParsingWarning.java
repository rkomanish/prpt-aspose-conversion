package com.migration.prpt2aspose.model;

/**
 * A single diagnostic recorded instead of throwing, so a partially-understood
 * report still produces a usable {@link ReportModel}. {@code category} is a
 * free-form short code (e.g. "UNSUPPORTED_ELEMENT", "SCRIPTED_EXPRESSION")
 * that migration-report.html groups by in a later phase.
 */
public record ParsingWarning(WarningSeverity severity, String category, String location, String message) {

    public static ParsingWarning warning(String category, String location, String message) {
        return new ParsingWarning(WarningSeverity.WARNING, category, location, message);
    }

    public static ParsingWarning info(String category, String location, String message) {
        return new ParsingWarning(WarningSeverity.INFO, category, location, message);
    }

    public static ParsingWarning error(String category, String location, String message) {
        return new ParsingWarning(WarningSeverity.ERROR, category, location, message);
    }
}
