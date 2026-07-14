package com.migration.prpt2aspose.model;

import java.util.List;

/** A band is a flat, ordered list of positioned elements of a given {@link BandType}. */
public record ReportBand(BandType type, List<ReportElement> elements) {

    public ReportBand {
        elements = List.copyOf(elements);
    }

    public static ReportBand empty(BandType type) {
        return new ReportBand(type, List.of());
    }
}
