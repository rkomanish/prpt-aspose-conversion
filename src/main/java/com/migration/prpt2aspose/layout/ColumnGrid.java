package com.migration.prpt2aspose.layout;

import java.util.List;

/**
 * The inferred column structure of the target worksheet: PRPT positions
 * elements by absolute x/width in points, Excel positions them by column, so
 * every distinct x-start (within tolerance) across all bands becomes one
 * column. {@code syntheticLeadingColumns} are extra columns prepended on the
 * left for group fields that were flattened out of group-header bands into
 * the detail row (Smart Markers group within the data row, not above it).
 */
public final class ColumnGrid {

    /** Points per Excel column-width character unit (~48pt default column / 8.43 chars). */
    private static final double POINTS_PER_CHAR = 5.7;
    private static final double DEFAULT_SYNTHETIC_WIDTH_CHARS = 22.0;

    private final List<Double> clusterStarts;
    private final double[] clusterWidthsPts;
    private final int syntheticLeadingColumns;

    ColumnGrid(List<Double> clusterStarts, double[] clusterWidthsPts, int syntheticLeadingColumns) {
        this.clusterStarts = List.copyOf(clusterStarts);
        this.clusterWidthsPts = clusterWidthsPts.clone();
        this.syntheticLeadingColumns = syntheticLeadingColumns;
    }

    public int columnCount() {
        return syntheticLeadingColumns + clusterStarts.size();
    }

    public int syntheticLeadingColumns() {
        return syntheticLeadingColumns;
    }

    /** Worksheet column for an element starting at {@code x} points. */
    public int columnFor(double x) {
        return syntheticLeadingColumns + nearestClusterIndex(x);
    }

    /** Last worksheet column an element covers, given its exclusive right edge in points. */
    public int lastColumnCovering(double xEnd) {
        int last = 0;
        for (int i = 0; i < clusterStarts.size(); i++) {
            if (clusterStarts.get(i) < xEnd - ColumnGridMapper.CLUSTER_TOLERANCE_PTS) {
                last = i;
            }
        }
        return syntheticLeadingColumns + last;
    }

    /** Excel column width in character units for the given worksheet column. */
    public double widthCharsFor(int column) {
        if (column < syntheticLeadingColumns) {
            return DEFAULT_SYNTHETIC_WIDTH_CHARS;
        }
        return Math.round(clusterWidthsPts[column - syntheticLeadingColumns] / POINTS_PER_CHAR * 100) / 100.0;
    }

    private int nearestClusterIndex(double x) {
        int best = 0;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < clusterStarts.size(); i++) {
            double distance = Math.abs(clusterStarts.get(i) - x);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return best;
    }
}
