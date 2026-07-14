package com.migration.prpt2aspose.layout;

import com.migration.prpt2aspose.model.GroupDefinition;
import com.migration.prpt2aspose.model.ReportBand;
import com.migration.prpt2aspose.model.ReportElement;
import com.migration.prpt2aspose.model.ReportModel;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the {@link ColumnGrid} for a report by clustering the x-starts of
 * every band element. An element's width only drives its own column's width
 * up to the next cluster's start, so wide spanning elements (e.g. a title
 * label across the whole page) become merged cells instead of inflating the
 * first column.
 */
public final class ColumnGridMapper {

    static final double CLUSTER_TOLERANCE_PTS = 3.0;

    public ColumnGrid build(ReportModel model, int syntheticLeadingColumns) {
        List<ReportElement> elements = collectElements(model);

        List<Double> starts = elements.stream()
                .map(e -> e.geometry().x())
                .sorted()
                .toList();

        List<Double> clusterStarts = new ArrayList<>();
        for (double x : starts) {
            if (clusterStarts.isEmpty() || x - clusterStarts.get(clusterStarts.size() - 1) > CLUSTER_TOLERANCE_PTS) {
                clusterStarts.add(x);
            }
        }
        if (clusterStarts.isEmpty()) {
            clusterStarts.add(0.0);
        }

        double[] widths = new double[clusterStarts.size()];
        for (ReportElement element : elements) {
            int cluster = nearest(clusterStarts, element.geometry().x());
            double width = element.geometry().width();
            if (cluster < clusterStarts.size() - 1) {
                width = Math.min(width, clusterStarts.get(cluster + 1) - clusterStarts.get(cluster));
            }
            widths[cluster] = Math.max(widths[cluster], width);
        }
        for (int i = 0; i < widths.length; i++) {
            if (widths[i] <= 0) {
                widths[i] = 48.0;
            }
        }

        return new ColumnGrid(clusterStarts, widths, syntheticLeadingColumns);
    }

    private List<ReportElement> collectElements(ReportModel model) {
        List<ReportElement> all = new ArrayList<>();
        all.addAll(model.reportHeaderBand().elements());
        all.addAll(model.pageHeaderBand().elements());
        collectGroupElements(model.groups(), all);
        all.addAll(model.reportFooterBand().elements());
        all.addAll(model.pageFooterBand().elements());
        return all;
    }

    private void collectGroupElements(List<GroupDefinition> groups, List<ReportElement> into) {
        for (GroupDefinition group : groups) {
            addBoundOnly(group.headerBand(), into);
            into.addAll(group.itemsBand().elements());
            into.addAll(group.footerBand().elements());
            collectGroupElements(group.subGroups(), into);
        }
    }

    /**
     * Group-header bound fields are flattened into synthetic leading columns
     * by the generator, so only their static labels participate in x-clustering.
     */
    private void addBoundOnly(ReportBand headerBand, List<ReportElement> into) {
        headerBand.elements().stream()
                .filter(e -> e.binding().fieldName() == null)
                .forEach(into::add);
    }

    private int nearest(List<Double> clusterStarts, double x) {
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
