package com.migration.prpt2aspose.generator;

import com.aspose.cells.Cell;
import com.aspose.cells.Cells;
import com.aspose.cells.CellsHelper;
import com.aspose.cells.Workbook;
import com.aspose.cells.Worksheet;
import com.migration.prpt2aspose.layout.ColumnGrid;
import com.migration.prpt2aspose.layout.ColumnGridMapper;
import com.migration.prpt2aspose.model.BandType;
import com.migration.prpt2aspose.model.ElementType;
import com.migration.prpt2aspose.model.FieldBindingType;
import com.migration.prpt2aspose.model.GroupDefinition;
import com.migration.prpt2aspose.model.ParsingWarning;
import com.migration.prpt2aspose.model.ReportBand;
import com.migration.prpt2aspose.model.ReportElement;
import com.migration.prpt2aspose.model.ReportModel;
import com.migration.prpt2aspose.styles.AsposeStyleMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The PRPT→Aspose adapter. Key layout transformations, each recorded in the
 * migration report rather than applied silently:
 *
 * <ul>
 *   <li><b>Group headers flatten into grouped columns.</b> Smart Markers group
 *       within the data row ({@code (group:normal)}), not via a separate
 *       header row, so bound fields in group-header bands become synthetic
 *       leading columns of the detail row.</li>
 *   <li><b>Group-footer {@code =SUM([X])} becomes {@code subtotal9}.</b> Aspose
 *       inserts per-group subtotal rows itself; the static footer band row is
 *       dropped in favor of the marker parameter.</li>
 *   <li><b>Report-footer {@code =SUM([X])} becomes
 *       {@code SUBTOTAL(9, first:INDEX(col,ROW()-1))}</b> so the range survives
 *       row insertion during marker expansion and ignores nested group
 *       subtotal rows.</li>
 *   <li><b>Bound fields outside repeating bands become scalar variables</b>
 *       ({@code &=$name}), to be bound via
 *       {@code WorkbookDesigner.setDataSource(name, value)}.</li>
 * </ul>
 */
@Component
public class AsposeSmartMarkerGenerator implements TemplateGenerator {

    private static final Logger log = LoggerFactory.getLogger(AsposeSmartMarkerGenerator.class);
    private static final String SHEET_NAME = "Template";

    private final AsposeStyleMapper styleMapper;
    private final ColumnGridMapper gridMapper = new ColumnGridMapper();

    public AsposeSmartMarkerGenerator(AsposeStyleMapper styleMapper) {
        this.styleMapper = styleMapper;
    }

    @Override
    public String targetName() {
        return "Aspose.Cells Smart Marker template";
    }

    @Override
    public GenerationResult generate(ReportModel model, Path outputDir) throws IOException {
        Files.createDirectories(outputDir);
        Path templateFile = outputDir.resolve("template.xlsx");

        Generation gen = new Generation(model);
        gen.run();
        try {
            gen.workbook.save(templateFile.toString());
        } catch (Exception e) {
            throw new IOException("Failed to save template to " + templateFile, e);
        }
        log.info("Generated {} ({} converted cells, {} warnings)",
                templateFile, gen.convertedItems.size(), gen.warnings.size());

        return new GenerationResult(templateFile, gen.convertedItems, gen.warnings, datasetMapping(model));
    }

    /** {"Orders": "OrdersData", ...} — dataset name to suggested Java-service data source bean name. */
    private Map<String, String> datasetMapping(ReportModel model) {
        Map<String, String> mapping = new LinkedHashMap<>();
        model.dataSources().forEach(ds -> ds.queries().forEach(q -> mapping.put(q.name(), q.name() + "Data")));
        return mapping;
    }

    /** Per-run mutable state, so the generator bean itself stays stateless. */
    private final class Generation {

        final ReportModel model;
        final Workbook workbook = new Workbook();
        final Worksheet sheet;
        final Cells cells;
        final String dataset;
        final List<String> flattenedGroupFields;
        final ColumnGrid grid;
        final List<String> convertedItems = new ArrayList<>();
        final List<ParsingWarning> warnings = new ArrayList<>();
        /** items-field → group field to subtotal by, harvested from group footers before emission. */
        final Map<String, String> subtotalsByField = new LinkedHashMap<>();
        int rowCursor = 0;
        int firstItemsRow = -1;
        int lastItemsRow = -1;

        Generation(ReportModel model) {
            this.model = model;
            this.sheet = workbook.getWorksheets().get(0);
            this.sheet.setName(SHEET_NAME);
            this.cells = sheet.getCells();
            this.dataset = primaryDataset(model);
            this.flattenedGroupFields = collectFlattenedGroupFields(model.groups());
            this.grid = gridMapper.build(model, flattenedGroupFields.size());
        }

        void run() {
            harvestSubtotals(model.groups());

            emitBandRow(model.reportHeaderBand(), BandContext.STATIC);
            emitPageHeaderRow();
            emitGroups(model.groups());
            emitBandRow(model.reportFooterBand(), BandContext.REPORT_FOOTER);
            emitPageFooter();

            applyColumnWidths();
            applyFreezePanes();
        }

        // ---- band emission -------------------------------------------------

        private void emitGroups(List<GroupDefinition> groups) {
            for (GroupDefinition group : groups) {
                ReportBand headerLeftovers = staticLeftoversOf(group.headerBand(), group);
                emitBandRow(headerLeftovers, BandContext.STATIC);
                if (group.subGroups().isEmpty()) {
                    emitItemsRow(group);
                } else {
                    emitGroups(group.subGroups());
                }
                emitGroupFooter(group);
            }
        }

        private void emitItemsRow(GroupDefinition group) {
            int row = rowCursor;
            if (firstItemsRow < 0) {
                firstItemsRow = row;
            }
            lastItemsRow = row;

            int syntheticCol = 0;
            for (String groupField : flattenedGroupFields) {
                put(row, syntheticCol++, SmartMarkerSyntax.groupedField(dataset, groupField),
                        boundStyleOf(group.headerBand(), groupField));
            }

            double maxHeight = 0;
            for (ReportElement element : group.itemsBand().elements()) {
                maxHeight = Math.max(maxHeight, element.geometry().height());
                int col = grid.columnFor(element.geometry().x());
                String content = itemsMarkerFor(element, group);
                if (content == null) {
                    if (element.binding().type() == FieldBindingType.NONE) {
                        reportUnconvertible(row, col, element);
                    }
                    continue;
                }
                put(row, col, content, element);
            }
            applyRowHeight(row, maxHeight);
            rowCursor++;
        }

        /** Marker text for a detail-row element, or null when the element carries nothing convertible. */
        private String itemsMarkerFor(ReportElement element, GroupDefinition group) {
            var binding = element.binding();
            String fieldName = binding.fieldName();
            if (binding.type() == FieldBindingType.FORMULA) {
                fieldName = SmartMarkerSyntax.asDirectFieldRef(binding.formula());
                if (fieldName == null) {
                    warn("MANUAL_FORMULA", "items:" + element.name(),
                            "Formula '" + binding.formula() + "' has no Smart Marker equivalent; compute it in SQL or the Java service.");
                    return null;
                }
            }
            if (binding.type() == FieldBindingType.EXPRESSION_REF) {
                warn("MANUAL_EXPRESSION", "items:" + element.name(),
                        "Element references expression '" + binding.expressionRef() + "'; precompute it as a query column.");
                return null;
            }
            if (fieldName == null) {
                return element.text(); // static label inside the detail row
            }

            String subtotalGroupField = subtotalsByField.get(fieldName);
            if (subtotalGroupField != null) {
                return SmartMarkerSyntax.subtotaledField(dataset, fieldName, subtotalGroupField);
            }
            if (group.groupingFields().contains(fieldName)) {
                return SmartMarkerSyntax.groupedField(dataset, fieldName);
            }
            return SmartMarkerSyntax.field(dataset, fieldName);
        }

        /**
         * Group footers don't exist as rows in Smart Marker templates: a
         * {@code =SUM([X])} converts to a subtotal9 marker parameter (harvested
         * up front by {@link #harvestSubtotals}); anything else is a manual fix.
         */
        private void emitGroupFooter(GroupDefinition group) {
            for (ReportElement element : group.footerBand().elements()) {
                String summedField = SmartMarkerSyntax.asSumOfField(element.binding().formula());
                if (summedField != null) {
                    info("GROUP_SUBTOTAL_CONVERTED", "groupFooter:" + element.name(),
                            "SUM(" + summedField + ") converted to an Aspose subtotal9 marker parameter; Aspose inserts the subtotal row per group.");
                } else if (element.binding().type() != FieldBindingType.NONE || element.type() != ElementType.LABEL) {
                    warn("MANUAL_GROUP_FOOTER", "groupFooter:" + element.name(),
                            "Group-footer element could not be converted (Smart Markers have no per-group footer row); handle manually.");
                }
                // static labels like "Subtotal:" are dropped: the subtotal row is generated by Aspose
            }
        }

        private void emitPageHeaderRow() {
            ReportBand band = model.pageHeaderBand();
            if (band.elements().isEmpty()) {
                return;
            }
            // Column-header labels for the synthetic (flattened group field) columns,
            // styled like the first real column header so the row reads as one unit.
            ReportElement styleDonor = band.elements().get(0);
            for (int i = 0; i < flattenedGroupFields.size(); i++) {
                put(rowCursor, i, flattenedGroupFields.get(i), styleDonor);
            }
            emitBandRow(band, BandContext.STATIC);
        }

        private void emitBandRow(ReportBand band, BandContext context) {
            if (band.elements().isEmpty()) {
                return;
            }
            int row = rowCursor;
            double maxHeight = 0;
            for (ReportElement element : band.elements()) {
                maxHeight = Math.max(maxHeight, element.geometry().height());
                int col = grid.columnFor(element.geometry().x());
                staticCell(row, col, element, context);
            }
            applyRowHeight(row, maxHeight);
            rowCursor++;
        }

        /** A cell in a non-repeating band: label text, scalar variable marker, or report-footer aggregate. */
        private void staticCell(int row, int col, ReportElement element, BandContext context) {
            var binding = element.binding();

            if (context == BandContext.REPORT_FOOTER && binding.type() == FieldBindingType.FORMULA) {
                String summedField = SmartMarkerSyntax.asSumOfField(binding.formula());
                if (summedField != null) {
                    putGrandTotalFormula(row, col, summedField, element);
                    return;
                }
            }
            if (binding.type() == FieldBindingType.DIRECT_FIELD) {
                put(row, col, SmartMarkerSyntax.variable(binding.fieldName()), element);
                info("SCALAR_VARIABLE", cellRef(row, col),
                        "Field '" + binding.fieldName() + "' outside a repeating band became scalar variable &=$"
                                + binding.fieldName() + "; bind it via WorkbookDesigner.setDataSource(name, value).");
                return;
            }
            if (binding.type() == FieldBindingType.FORMULA || binding.type() == FieldBindingType.EXPRESSION_REF) {
                warn("MANUAL_STATIC_BINDING", cellRef(row, col),
                        "Element '" + element.name() + "' uses " + binding.type() + " outside a repeating band; convert manually.");
                return;
            }
            if (element.text() != null) {
                put(row, col, element.text(), element);
                mergeIfSpanning(row, col, element);
                return;
            }
            reportUnconvertible(row, col, element);
        }

        /** Requirement: nothing is dropped silently — content-less graphics land in the migration report instead. */
        private void reportUnconvertible(int row, int col, ReportElement element) {
            switch (element.type()) {
                case IMAGE_FIELD -> warn("MANUAL_IMAGE", cellRef(row, col),
                        "Image element '" + element.name() + "'"
                                + srcSuffix(element) + " is not auto-converted; insert the picture into the template manually"
                                + " (Aspose: worksheet.getPictures().add(...)).");
                case LINE -> info("LINE_DROPPED", cellRef(row, col),
                        "Line element '" + element.name() + "' has no Smart Marker equivalent; use a cell border in the template if needed.");
                case RECTANGLE -> info("RECTANGLE_DROPPED", cellRef(row, col),
                        "Rectangle element '" + element.name() + "' was dropped; its fill is usually covered by cell background styling.");
                default -> warn("UNCONVERTED_ELEMENT", cellRef(row, col),
                        "Element '" + element.name() + "' (" + element.rawTagName() + ") carries no text or field binding and was not converted.");
            }
        }

        private String srcSuffix(ReportElement element) {
            String src = element.style().rawAttributes().get("src");
            return src == null ? "" : " (src=" + src + ")";
        }

        private void putGrandTotalFormula(int row, int col, String summedField, ReportElement element) {
            int fieldCol = detailColumnOf(summedField);
            if (fieldCol < 0 || firstItemsRow < 0) {
                warn("MANUAL_AGGREGATE", cellRef(row, col),
                        "SUM([" + summedField + "]) has no matching detail-row column; add the aggregate manually.");
                return;
            }
            String colName = CellsHelper.columnIndexToName(fieldCol);
            // SUBTOTAL ignores Aspose-inserted group subtotal rows; the INDEX upper
            // bound tracks row insertions during marker expansion.
            String formula = "=SUBTOTAL(9," + colName + (firstItemsRow + 1)
                    + ":INDEX(" + colName + ":" + colName + ",ROW()-1))";
            Cell cell = cells.get(row, col);
            cell.setFormula(formula);
            applyStyle(cell, element);
            convertedItems.add(cellRef(row, col) + " = " + formula + "  (from =SUM([" + summedField + "]))");
        }

        private void emitPageFooter() {
            ReportBand band = model.pageFooterBand();
            if (band.elements().isEmpty()) {
                return;
            }
            boolean allStaticLabels = band.elements().stream()
                    .allMatch(e -> e.type() == ElementType.LABEL && e.text() != null);
            if (allStaticLabels) {
                for (ReportElement element : band.elements()) {
                    int section = element.geometry().x() < 150 ? 0 : element.geometry().x() < 300 ? 1 : 2;
                    sheet.getPageSetup().setFooter(section, element.text());
                    convertedItems.add("PageFooter[" + section + "] = \"" + element.text() + "\"");
                }
                info("PAGE_FOOTER_AS_PRINT_FOOTER", "pageFooter",
                        "Page-footer labels mapped to the Excel print footer (visible in print/preview, not in the grid).");
            } else {
                emitBandRow(band, BandContext.STATIC);
                warn("PAGE_FOOTER_AS_ROW", "pageFooter",
                        "Page footer contains non-label content; emitted as a trailing sheet row instead of the print footer.");
            }
        }

        // ---- flattening & subtotal harvesting -------------------------------

        private void harvestSubtotals(List<GroupDefinition> groups) {
            for (GroupDefinition group : groups) {
                String groupField = group.groupingFields().isEmpty()
                        ? flattenedGroupFields.isEmpty() ? null : flattenedGroupFields.get(0)
                        : group.groupingFields().get(0);
                for (ReportElement element : group.footerBand().elements()) {
                    String summedField = SmartMarkerSyntax.asSumOfField(element.binding().formula());
                    if (summedField != null && groupField != null) {
                        subtotalsByField.put(summedField, groupField);
                    }
                }
                harvestSubtotals(group.subGroups());
            }
        }

        /** The group-header band minus the bound fields that were flattened into detail-row columns. */
        private ReportBand staticLeftoversOf(ReportBand headerBand, GroupDefinition group) {
            List<ReportElement> leftovers = headerBand.elements().stream()
                    .filter(e -> e.binding().fieldName() == null)
                    .toList();
            int flattened = headerBand.elements().size() - leftovers.size();
            if (flattened > 0) {
                warn("GROUP_HEADER_FLATTENED", "group:" + group.name(),
                        flattened + " bound field(s) moved from the group-header band into grouped detail-row column(s) "
                                + "(Smart Markers group within the data row); review the layout difference.");
            }
            if (!leftovers.isEmpty()) {
                warn("GROUP_HEADER_STATIC_ONCE", "group:" + group.name(),
                        "Static group-header content is emitted once above the data rows, not once per group.");
            }
            return new ReportBand(BandType.GROUP_HEADER, leftovers);
        }

        // ---- cell/format plumbing -------------------------------------------

        private void put(int row, int col, String content, ReportElement styleSource) {
            Cell cell = cells.get(row, col);
            cell.putValue(content);
            applyStyle(cell, styleSource);
            convertedItems.add(cellRef(row, col) + " = \"" + content + "\"");
        }

        private ReportElement boundStyleOf(ReportBand band, String fieldName) {
            return band.elements().stream()
                    .filter(e -> fieldName.equals(e.binding().fieldName()))
                    .findFirst()
                    .orElse(null);
        }

        private void applyStyle(Cell cell, ReportElement element) {
            if (element == null) {
                return;
            }
            cell.setStyle(styleMapper.toAsposeStyle(workbook, element.style(), element.type()));
        }

        private void mergeIfSpanning(int row, int col, ReportElement element) {
            int lastCol = grid.lastColumnCovering(element.geometry().x() + element.geometry().width());
            if (lastCol > col) {
                cells.merge(row, col, 1, lastCol - col + 1);
            }
        }

        private void applyRowHeight(int row, double heightPts) {
            if (heightPts > 0) {
                cells.setRowHeight(row, heightPts);
            }
        }

        private void applyColumnWidths() {
            for (int col = 0; col < grid.columnCount(); col++) {
                cells.setColumnWidth(col, grid.widthCharsFor(col));
            }
        }

        private void applyFreezePanes() {
            if (firstItemsRow > 0) {
                sheet.freezePanes(firstItemsRow, 0, firstItemsRow, 0);
            }
        }

        private String primaryDataset(ReportModel model) {
            return model.dataSources().stream()
                    .flatMap(ds -> ds.queries().stream())
                    .findFirst()
                    .map(q -> q.name())
                    .orElseGet(() -> {
                        warn("NO_DATASET", "report", "Report has no SQL query; markers use dataset name 'Data'.");
                        return "Data";
                    });
        }

        private List<String> collectFlattenedGroupFields(List<GroupDefinition> groups) {
            Set<String> fields = new LinkedHashSet<>();
            collectFlattenedGroupFields(groups, fields);
            return List.copyOf(fields);
        }

        private void collectFlattenedGroupFields(List<GroupDefinition> groups, Set<String> into) {
            for (GroupDefinition group : groups) {
                group.headerBand().elements().stream()
                        .map(e -> e.binding().fieldName())
                        .filter(f -> f != null)
                        .forEach(into::add);
                collectFlattenedGroupFields(group.subGroups(), into);
            }
        }

        private String cellRef(int row, int col) {
            return SHEET_NAME + "!" + CellsHelper.columnIndexToName(col) + (row + 1);
        }

        private int detailColumnOf(String fieldName) {
            int synthetic = flattenedGroupFields.indexOf(fieldName);
            if (synthetic >= 0) {
                return synthetic;
            }
            return findDetailColumn(model.groups(), fieldName);
        }

        private int findDetailColumn(List<GroupDefinition> groups, String fieldName) {
            for (GroupDefinition group : groups) {
                for (ReportElement element : group.itemsBand().elements()) {
                    String bound = element.binding().fieldName() != null
                            ? element.binding().fieldName()
                            : SmartMarkerSyntax.asDirectFieldRef(element.binding().formula());
                    if (fieldName.equals(bound)) {
                        return grid.columnFor(element.geometry().x());
                    }
                }
                int fromSub = findDetailColumn(group.subGroups(), fieldName);
                if (fromSub >= 0) {
                    return fromSub;
                }
            }
            return -1;
        }

        private void warn(String category, String location, String message) {
            warnings.add(ParsingWarning.warning(category, location, message));
        }

        private void info(String category, String location, String message) {
            warnings.add(ParsingWarning.info(category, location, message));
        }
    }

    private enum BandContext {
        STATIC,
        REPORT_FOOTER
    }
}
