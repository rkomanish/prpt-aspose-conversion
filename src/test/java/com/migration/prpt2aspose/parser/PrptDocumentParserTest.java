package com.migration.prpt2aspose.parser;

import com.migration.prpt2aspose.model.ReportModel;
import com.migration.prpt2aspose.testsupport.PrptFixtures;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PrptDocumentParserTest {

    private final PrptDocumentParser parser = new PrptDocumentParser();

    @Test
    void parsesFullReportEndToEnd() {
        ReportModel model = parser.parse(new ByteArrayInputStream(PrptFixtures.minimalReportWithManifest()), "fixture.prpt");

        assertThat(model.reportName()).isEqualTo("Customer Statement");
        assertThat(model.parameters()).hasSize(1);
        assertThat(model.expressions()).hasSize(2);
        assertThat(model.totalQueryCount()).isEqualTo(1);
        assertThat(model.groups()).hasSize(1);

        // one SCRIPTED_EXPRESSION warning + one UNSUPPORTED_ELEMENT warning expected, no ERROR-level warnings
        assertThat(model.warnings()).isNotEmpty();
        assertThat(model.warnings()).noneMatch(w -> w.severity() == com.migration.prpt2aspose.model.WarningSeverity.ERROR);
        assertThat(model.warnings()).anySatisfy(w -> assertThat(w.category()).isEqualTo("SCRIPTED_EXPRESSION"));
        assertThat(model.warnings()).anySatisfy(w -> assertThat(w.category()).isEqualTo("UNSUPPORTED_ELEMENT"));
    }

    @Test
    void recordsManifestMissingWarningButStillParses() {
        ReportModel model = parser.parse(new ByteArrayInputStream(PrptFixtures.reportWithoutManifest()), "no-manifest.prpt");

        assertThat(model.reportName()).isEqualTo("Customer Statement");
        assertThat(model.warnings()).anySatisfy(w -> assertThat(w.category()).isEqualTo("MANIFEST_MISSING"));
    }

    @Test
    void resolvesStandaloneDataSourcesFile() {
        ReportModel model = parser.parse(new ByteArrayInputStream(PrptFixtures.reportWithStandaloneDataSources()), "standalone-ds.prpt");

        assertThat(model.totalQueryCount()).isEqualTo(1);
        assertThat(model.dataSources().get(0).queries().get(0).name()).isEqualTo("Orders");
    }

    @Test
    void throwsWhenNoReportDefinitionIsFound() {
        assertThatThrownBy(() -> parser.parse(new ByteArrayInputStream(PrptFixtures.emptyArchive()), "empty.prpt"))
                .isInstanceOf(PrptParsingException.class)
                .hasMessageContaining("No report definition");
    }
}
