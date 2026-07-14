package com.migration.prpt2aspose.parser;

import com.migration.prpt2aspose.testsupport.PrptFixtures;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class PrptArchiveReaderTest {

    @Test
    void resolvesReportDefinitionViaManifestMediaType() throws IOException {
        PrptArchiveReader reader = PrptArchiveReader.open(new ByteArrayInputStream(PrptFixtures.minimalReportWithManifest()));

        assertThat(reader.hasManifest()).isTrue();
        assertThat(reader.findReportDefinitionPart()).isPresent();
        assertThat(reader.findReportDefinitionPart().get().name()).isEqualTo("content.xml");
    }

    @Test
    void fallsBackToFilenameGuessingWhenManifestMissing() throws IOException {
        PrptArchiveReader reader = PrptArchiveReader.open(new ByteArrayInputStream(PrptFixtures.reportWithoutManifest()));

        assertThat(reader.hasManifest()).isFalse();
        assertThat(reader.findReportDefinitionPart()).isPresent();
        assertThat(reader.findReportDefinitionPart().get().name()).isEqualTo("content.xml");
    }

    @Test
    void resolvesStandaloneDataSourcesPart() throws IOException {
        PrptArchiveReader reader = PrptArchiveReader.open(new ByteArrayInputStream(PrptFixtures.reportWithStandaloneDataSources()));

        assertThat(reader.findDataSourcesPart()).isPresent();
        assertThat(reader.findDataSourcesPart().get().name()).isEqualTo("datasources.xml");
    }

    @Test
    void emptyArchiveHasNoReportDefinition() throws IOException {
        PrptArchiveReader reader = PrptArchiveReader.open(new ByteArrayInputStream(PrptFixtures.emptyArchive()));

        assertThat(reader.findReportDefinitionPart()).isEmpty();
    }
}
