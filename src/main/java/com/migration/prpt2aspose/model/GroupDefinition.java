package com.migration.prpt2aspose.model;

import java.util.List;

/**
 * A PRPT {@code <group>}: the fields it groups by, its header/footer bands,
 * and either a repeating {@code itemsBand} (innermost group) or nested
 * {@code subGroups} (outer groups in a multi-level grouping).
 */
public record GroupDefinition(
        String name,
        List<String> groupingFields,
        ReportBand headerBand,
        ReportBand itemsBand,
        ReportBand footerBand,
        List<GroupDefinition> subGroups) {

    public GroupDefinition {
        groupingFields = List.copyOf(groupingFields);
        subGroups = List.copyOf(subGroups);
    }
}
