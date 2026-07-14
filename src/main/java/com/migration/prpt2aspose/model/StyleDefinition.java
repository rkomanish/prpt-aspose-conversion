package com.migration.prpt2aspose.model;

import java.util.Map;

/**
 * Raw style facts captured verbatim from PRPT at Phase 1. Deliberately not
 * yet mapped onto Aspose font/border/color types — that translation is
 * Phase 6's job. {@code rawAttributes} preserves anything this phase doesn't
 * model explicitly, so no style information is silently dropped.
 */
public record StyleDefinition(
        String fontFamily,
        Double fontSize,
        boolean bold,
        boolean italic,
        String textAlign,
        String backgroundColor,
        Map<String, String> rawAttributes) {

    public static final StyleDefinition EMPTY =
            new StyleDefinition(null, null, false, false, null, null, Map.of());
}
