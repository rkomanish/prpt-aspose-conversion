package com.migration.prpt2aspose.model;

/**
 * Mirrors the band vocabulary of a Pentaho master-report: report-level
 * header/footer, page-level header/footer, and per-group header/items/footer.
 * ITEMS is the repeating detail band — the direct analogue of a Smart Marker
 * repeating region.
 */
public enum BandType {
    REPORT_HEADER,
    REPORT_FOOTER,
    PAGE_HEADER,
    PAGE_FOOTER,
    GROUP_HEADER,
    GROUP_FOOTER,
    ITEMS
}
