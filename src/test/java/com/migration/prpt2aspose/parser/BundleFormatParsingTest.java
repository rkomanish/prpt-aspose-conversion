package com.migration.prpt2aspose.parser;

import com.migration.prpt2aspose.converter.ConversionService;
import com.migration.prpt2aspose.generator.AsposeSmartMarkerGenerator;
import com.migration.prpt2aspose.mapping.MappingJsonWriter;
import com.migration.prpt2aspose.model.FieldBindingType;
import com.migration.prpt2aspose.model.GroupDefinition;
import com.migration.prpt2aspose.model.ReportElement;
import com.migration.prpt2aspose.model.ReportModel;
import com.migration.prpt2aspose.report.MigrationReportWriter;
import com.migration.prpt2aspose.report.QueriesSqlWriter;
import com.migration.prpt2aspose.styles.AsposeStyleMapper;
import com.migration.prpt2aspose.testsupport.PrptBundleFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The real-world contract: a Report-Designer bundle (layout.xml +
 * datadefinition.xml + datasources/ + meta.xml, namespaced, with structured
 * <element> nodes) must parse into the same ReportModel shape the synthetic
 * format produces, and convert end-to-end into a Smart Marker template.
 */
class BundleFormatParsingTest {

    private final PrptDocumentParser parser = new PrptDocumentParser();

    @Test
    void parsesRealBundleLayout() {
        ReportModel model = parser.parse(
                new ByteArrayInputStream(PrptBundleFixtures.realisticBundle()), "bundle.prpt");

        assertThat(model.reportName()).isEqualTo("Monthly Borrower Overview");

        assertThat(model.parameters()).hasSize(1);
        assertThat(model.parameters().get(0).name()).isEqualTo("asOfDate");
        assertThat(model.parameters().get(0).type()).isEqualTo("date");
        assertThat(model.parameters().get(0).mandatory()).isTrue();

        assertThat(model.expressions()).hasSize(1);
        assertThat(model.expressions().get(0).expressionBody()).isEqualTo("=SUM([EXPOSURE_AMT])");

        assertThat(model.totalQueryCount()).isEqualTo(1);
        var query = model.dataSources().get(0).queries().get(0);
        assertThat(query.name()).isEqualTo("BorrowerQuery");
        assertThat(query.sql()).contains("FROM   borrower_overview", "${asOfDate}");

        // report header: label with normalized geometry/style from <style>/<attributes> children
        ReportElement title = model.reportHeaderBand().elements().get(0);
        assertThat(title.text()).isEqualTo("Monthly Borrower Overview");
        assertThat(title.geometry().width()).isEqualTo(300);
        assertThat(title.style().bold()).isTrue();
        assertThat(title.style().fontFamily()).isEqualTo("Arial");
        assertThat(title.style().fontSize()).isEqualTo(15);

        // relational-group with group-body > itemband
        assertThat(model.groups()).hasSize(1);
        GroupDefinition group = model.groups().get(0);
        assertThat(group.name()).isEqualTo("SectorGroup");
        assertThat(group.groupingFields()).containsExactly("SECTOR");
        assertThat(group.itemsBand().elements()).hasSize(2);

        ReportElement borrower = group.itemsBand().elements().get(0);
        assertThat(borrower.binding().type()).isEqualTo(FieldBindingType.DIRECT_FIELD);
        assertThat(borrower.binding().fieldName()).isEqualTo("BORROWER_NAME");

        ReportElement exposure = group.itemsBand().elements().get(1);
        assertThat(exposure.binding().fieldName()).isEqualTo("EXPOSURE_AMT");
        assertThat(exposure.geometry().x()).isEqualTo(180);
        assertThat(exposure.style().textAlign()).isEqualTo("right");
        assertThat(exposure.style().rawAttributes()).containsEntry("format", "#,##0.00");

        // group footer <message> normalized to a label with its text
        assertThat(group.footerBand().elements().get(0).text()).isEqualTo("Sector total:");
    }

    @Test
    void convertsBundleEndToEndWithMarkersAndResources(@TempDir Path tempDir) throws Exception {
        Path prpt = tempDir.resolve("monthly-borrower.prpt");
        Files.write(prpt, PrptBundleFixtures.realisticBundle());

        ConversionService service = new ConversionService(
                parser,
                new AsposeSmartMarkerGenerator(new AsposeStyleMapper()),
                new QueriesSqlWriter(),
                new MappingJsonWriter(),
                new MigrationReportWriter());
        Path outDir = tempDir.resolve("out");
        var result = service.convert(prpt, outDir);

        assertThat(result.generation().convertedItems()).isNotEmpty();
        assertThat(Files.readString(outDir.resolve("queries.sql"))).contains("borrower_overview");

        // markers bound to the primary query from datadefinition.xml
        var workbook = new com.aspose.cells.Workbook(outDir.resolve("template.xlsx").toString());
        var cells = workbook.getWorksheets().get("Template").getCells();
        StringBuilder all = new StringBuilder();
        for (int r = 0; r <= cells.getMaxDataRow(); r++) {
            for (int c = 0; c <= cells.getMaxDataColumn(); c++) {
                var cell = cells.checkCell(r, c);
                if (cell != null && cell.getStringValue() != null) {
                    all.append(cell.getStringValue()).append("|");
                }
            }
        }
        assertThat(all.toString())
                .contains("&=BorrowerQuery.BORROWER_NAME")
                .contains("&=BorrowerQuery.EXPOSURE_AMT")
                .contains("&=BorrowerQuery.SECTOR(group:normal)");

        // the embedded Excel template must survive the migration
        assertThat(outDir.resolve("resources/embedded-template.xlsx")).exists();
    }
}
