package com.migration.prpt2aspose.model;

/**
 * A single positioned element inside a band (label, text-field, number-field,
 * date-field, image-field, ...). {@code rawTagName} is kept even when
 * {@code type} resolves to a known {@link ElementType}, since it's useful for
 * diagnostics and for any later phase that needs the original tag.
 * {@code text} is the static display text (labels' value); null for
 * purely data-bound elements.
 */
public record ReportElement(
        String name,
        ElementType type,
        String rawTagName,
        Geometry geometry,
        FieldBinding binding,
        StyleDefinition style,
        String text) {
}
