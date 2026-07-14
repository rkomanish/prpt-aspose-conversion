package com.migration.prpt2aspose.parser;

import com.migration.prpt2aspose.model.BandType;
import com.migration.prpt2aspose.model.ElementType;
import com.migration.prpt2aspose.model.GroupDefinition;
import com.migration.prpt2aspose.model.ParsingWarning;
import com.migration.prpt2aspose.model.ReportModel;
import com.migration.prpt2aspose.testsupport.PrptFixtures;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReportXmlParserTest {

    private Document reportDocumentFromFixture(byte[] archiveBytes) throws Exception {
        PrptArchiveReader reader = PrptArchiveReader.open(new ByteArrayInputStream(archiveBytes));
        return reader.findReportDefinitionPart().orElseThrow().parseDocument();
    }

    @Test
    void extractsParametersExpressionsBandsAndGroups() throws Exception {
        Document doc = reportDocumentFromFixture(PrptFixtures.minimalReportWithManifest());
        ReportModel.Builder builder = ReportModel.builder();
        List<ParsingWarning> warnings = new ArrayList<>();

        new ReportXmlParser().populate(doc, builder, warnings);
        ReportModel model = builder.build();

        assertThat(model.reportName()).isEqualTo("Customer Statement");

        assertThat(model.parameters()).hasSize(1);
        assertThat(model.parameters().get(0).name()).isEqualTo("fromDate");
        assertThat(model.parameters().get(0).mandatory()).isTrue();

        assertThat(model.expressions()).hasSize(2);
        assertThat(model.expressions()).anySatisfy(e -> assertThat(e.isScripted()).isTrue());
        assertThat(warnings).anySatisfy(w -> assertThat(w.category()).isEqualTo("SCRIPTED_EXPRESSION"));

        assertThat(model.reportHeaderBand().elements()).hasSize(1);
        assertThat(model.reportHeaderBand().elements().get(0).type()).isEqualTo(ElementType.LABEL);

        assertThat(model.groups()).hasSize(1);
        GroupDefinition group = model.groups().get(0);
        assertThat(group.name()).isEqualTo("CustomerGroup");
        assertThat(group.groupingFields()).containsExactly("CustomerId");
        assertThat(group.headerBand().type()).isEqualTo(BandType.GROUP_HEADER);
        assertThat(group.headerBand().elements()).hasSize(1);
        assertThat(group.itemsBand().type()).isEqualTo(BandType.ITEMS);
        assertThat(group.itemsBand().elements()).hasSize(3);
        assertThat(group.footerBand().elements()).hasSize(1);

        // the deliberately unrecognized <weird-custom-element> in the items band
        assertThat(group.itemsBand().elements())
                .anySatisfy(el -> assertThat(el.type()).isEqualTo(ElementType.UNKNOWN));
        assertThat(warnings).anySatisfy(w -> assertThat(w.category()).isEqualTo("UNSUPPORTED_ELEMENT"));
    }

    @Test
    void missingItemsBandAndNoSubgroupsProducesWarning() throws Exception {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <master-report name="Empty Group Report">
                    <groups>
                        <group name="LonelyGroup">
                            <group-header-band/>
                        </group>
                    </groups>
                </master-report>
                """;
        Document doc = com.migration.prpt2aspose.util.SecureXmlSupport.parse(
                new ByteArrayInputStream(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)));

        ReportModel.Builder builder = ReportModel.builder();
        List<ParsingWarning> warnings = new ArrayList<>();
        new ReportXmlParser().populate(doc, builder, warnings);

        assertThat(warnings).anySatisfy(w -> assertThat(w.category()).isEqualTo("GROUP_MISSING_ITEMS_BAND"));
    }
}
