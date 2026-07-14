package com.migration.prpt2aspose.generator;

import com.aspose.cells.Workbook;
import com.aspose.cells.Worksheet;
import com.migration.prpt2aspose.model.ReportModel;
import com.migration.prpt2aspose.parser.PrptDocumentParser;
import com.migration.prpt2aspose.styles.AsposeStyleMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Generates a template from the dummy customer-orders.prpt sample and
 * verifies the emitted Smart Markers, layout transformations, formats, and
 * artifacts by reloading the workbook with Aspose.
 */
class AsposeSmartMarkerGeneratorTest {

    private static final Path SAMPLE = Path.of("src/test/resources/samples/customer-orders.prpt");

    private static GenerationResult result;
    private static Worksheet sheet;

    @TempDir
    static Path outputDir;

    @BeforeAll
    static void generateOnce() throws Exception {
        ReportModel model = new PrptDocumentParser().parse(SAMPLE);
        AsposeSmartMarkerGenerator generator = new AsposeSmartMarkerGenerator(new AsposeStyleMapper());
        result = generator.generate(model, outputDir);
        Workbook workbook = new Workbook(result.templateFile().toString());
        sheet = workbook.getWorksheets().get("Template");
    }

    @Test
    void emitsTitleThenColumnHeadersThenDetailRow() {
        assertThat(cell(0, 1)).isEqualTo("Customer Orders Report");
        // synthetic grouped column header + original page-header labels shifted right
        assertThat(cell(1, 0)).isEqualTo("CustomerName");
        assertThat(cell(1, 1)).isEqualTo("Order ID");
        assertThat(cell(1, 2)).isEqualTo("Order Date");
        assertThat(cell(1, 3)).isEqualTo("Amount");
    }

    @Test
    void detailRowCarriesSmartMarkersWithGroupingAndSubtotal() {
        assertThat(cell(2, 0)).isEqualTo("&=Orders.CustomerName(group:normal)");
        assertThat(cell(2, 1)).isEqualTo("&=Orders.OrderId");
        assertThat(cell(2, 2)).isEqualTo("&=Orders.OrderDate");
        // group footer =SUM([OrderAmount]) became a subtotal9 marker parameter
        assertThat(cell(2, 3)).isEqualTo("&=Orders.OrderAmount(subtotal9:Orders.CustomerName)");
    }

    @Test
    void reportFooterSumBecomesExpansionSafeSubtotalFormula() {
        var cells = sheet.getCells();
        boolean found = false;
        for (int row = 3; row <= 5; row++) {
            String formula = cells.get(row, 3).getFormula();
            if (formula != null && formula.contains("SUBTOTAL(9,D3:INDEX(D:D,ROW()-1))")) {
                found = true;
            }
        }
        assertThat(found).as("grand-total SUBTOTAL formula in column D").isTrue();
    }

    @Test
    void preservesStylesFreezePanesAndFormats() {
        // title: Arial 16 bold
        var titleStyle = sheet.getCells().get(0, 1).getStyle();
        assertThat(titleStyle.getFont().isBold()).isTrue();
        assertThat(titleStyle.getFont().getDoubleSize()).isEqualTo(16.0);
        assertThat(titleStyle.getFont().getName()).isEqualTo("Arial");

        // detail date format lowercased for Excel
        var dateStyle = sheet.getCells().get(2, 2).getStyle();
        assertThat(dateStyle.getCustom()).isEqualTo("yyyy-mm-dd");

        // panes frozen above the detail row
        assertThat(sheet.getPaneState()).isEqualTo(com.aspose.cells.PaneStateType.FROZEN);
    }

    @Test
    void pageFooterLabelLandsInPrintFooter() {
        assertThat(sheet.getPageSetup().getFooter(0)).contains("Acme Corp - Confidential");
    }

    @Test
    void mappingCoversAllQueriesAndTransformationsAreReported() throws Exception {
        assertThat(result.datasetMapping())
                .containsEntry("Orders", "OrdersData")
                .containsEntry("Customers", "CustomersData");
        assertThat(result.warnings())
                .anyMatch(w -> w.category().equals("GROUP_HEADER_FLATTENED"))
                .anyMatch(w -> w.category().equals("GROUP_SUBTOTAL_CONVERTED"));
        assertThat(Files.exists(result.templateFile())).isTrue();
    }

    private String cell(int row, int col) {
        return sheet.getCells().get(row, col).getStringValue();
    }
}
