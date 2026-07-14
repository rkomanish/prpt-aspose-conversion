package com.migration.prpt2aspose.generator;

import com.aspose.cells.ICellsDataTable;
import com.aspose.cells.Workbook;
import com.aspose.cells.WorkbookDesigner;
import com.aspose.cells.Worksheet;
import com.migration.prpt2aspose.model.ReportModel;
import com.migration.prpt2aspose.parser.PrptDocumentParser;
import com.migration.prpt2aspose.styles.AsposeStyleMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The end-to-end proof: generate a template from the dummy sample, then run
 * it through Aspose's Smart Marker processor with in-memory data — exactly
 * what the target Java service will do — and assert the populated output.
 */
class SmartMarkerProcessingIntegrationTest {

    @TempDir
    Path outputDir;

    @Test
    void flatProductListTemplatePopulatesOneRowPerRecord() throws Exception {
        ReportModel model = new PrptDocumentParser().parse(Path.of("src/test/resources/samples/product-list.prpt"));
        GenerationResult generation = new AsposeSmartMarkerGenerator(new AsposeStyleMapper()).generate(model, outputDir);

        WorkbookDesigner designer = new WorkbookDesigner(new Workbook(generation.templateFile().toString()));
        designer.setDataSource("Products", table(
                new String[]{"Sku", "ProductName", "UnitPrice", "UnitsInStock"},
                new Object[][]{
                        {"A-100", "Anvil", 55.20, 12},
                        {"B-200", "Bolt cutter", 23.10, 40},
                        {"C-300", "Crowbar", 18.75, 7},
                }));
        designer.process();

        Worksheet sheet = designer.getWorkbook().getWorksheets().get("Template");
        // row 0 title, row 1 column headers, rows 2..4 data
        assertThat(sheet.getCells().get(2, 0).getStringValue()).isEqualTo("A-100");
        assertThat(sheet.getCells().get(3, 1).getStringValue()).isEqualTo("Bolt cutter");
        assertThat(sheet.getCells().get(4, 2).getDoubleValue()).isEqualTo(18.75);
        assertThat(sheet.getCells().get(4, 3).getIntValue()).isEqualTo(7);
    }

    @Test
    void groupedCustomerOrdersTemplateGroupsAndTotals() throws Exception {
        ReportModel model = new PrptDocumentParser().parse(Path.of("src/test/resources/samples/customer-orders.prpt"));
        GenerationResult generation = new AsposeSmartMarkerGenerator(new AsposeStyleMapper()).generate(model, outputDir);

        WorkbookDesigner designer = new WorkbookDesigner(new Workbook(generation.templateFile().toString()));
        designer.setDataSource("Orders", table(
                new String[]{"CustomerName", "OrderId", "OrderDate", "OrderAmount"},
                new Object[][]{
                        {"Acme", 1, "2026-01-10", 100.0},
                        {"Acme", 2, "2026-02-11", 150.0},
                        {"Zenith", 3, "2026-03-12", 200.0},
                }));
        designer.process();

        Workbook workbook = designer.getWorkbook();
        workbook.calculateFormula();
        Worksheet sheet = workbook.getWorksheets().get("Template");

        String dump = dump(sheet, 12);
        System.out.println(dump);

        // all three orders present
        assertThat(dump).contains("Acme").contains("Zenith");
        assertThat(dump).contains("100").contains("150").contains("200");

        // grand total = 450 somewhere in the amount column (SUBTOTAL ignores group subtotal rows)
        boolean grandTotalFound = false;
        for (int row = 0; row < 15; row++) {
            var cell = sheet.getCells().get(row, 3);
            if (cell.getFormula() != null && cell.getDoubleValue() == 450.0) {
                grandTotalFound = true;
            }
        }
        assertThat(grandTotalFound).as("grand total cell evaluating to 450.0\n" + dump).isTrue();
    }

    private String dump(Worksheet sheet, int rows) {
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < rows; r++) {
            sb.append("row ").append(r).append(": ");
            for (int c = 0; c < 5; c++) {
                var cell = sheet.getCells().get(r, c);
                String formula = cell.getFormula();
                sb.append("[").append(formula != null ? formula + "=" : "").append(cell.getStringValue()).append("] ");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /** Minimal in-memory ICellsDataTable, standing in for the Java service's real data source. */
    private static ICellsDataTable table(String[] columns, Object[][] rows) {
        return new ICellsDataTable() {
            private int cursor = -1;

            @Override
            public String[] getColumns() {
                return columns;
            }

            @Override
            public int getCount() {
                return rows.length;
            }

            @Override
            public void beforeFirst() {
                cursor = -1;
            }

            @Override
            public Object get(int columnIndex) {
                return rows[cursor][columnIndex];
            }

            @Override
            public Object get(String columnName) {
                return get(List.of(columns).indexOf(columnName));
            }

            @Override
            public boolean next() {
                return ++cursor < rows.length;
            }
        };
    }
}
