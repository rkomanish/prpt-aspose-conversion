package com.migration.prpt2aspose.parser;

import com.migration.prpt2aspose.model.DataSourceDefinition;
import com.migration.prpt2aspose.model.ParsingWarning;
import com.migration.prpt2aspose.testsupport.PrptFixtures;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DataSourceXmlParserTest {

    @Test
    void extractsInlineQueries() throws Exception {
        PrptArchiveReader reader = PrptArchiveReader.open(new ByteArrayInputStream(PrptFixtures.minimalReportWithManifest()));
        Document reportDoc = reader.findReportDefinitionPart().orElseThrow().parseDocument();

        List<ParsingWarning> warnings = new ArrayList<>();
        List<DataSourceDefinition> dataSources = new DataSourceXmlParser().parse(reportDoc.getDocumentElement(), warnings);

        assertThat(dataSources).hasSize(1);
        assertThat(dataSources.get(0).queries()).hasSize(1);
        assertThat(dataSources.get(0).queries().get(0).name()).isEqualTo("Customers");
        assertThat(dataSources.get(0).queries().get(0).sql()).contains("SELECT * FROM customers");
        assertThat(warnings).isEmpty();
    }

    @Test
    void extractsQueriesFromStandaloneDataSourcesDocument() throws Exception {
        PrptArchiveReader reader = PrptArchiveReader.open(new ByteArrayInputStream(PrptFixtures.reportWithStandaloneDataSources()));
        Document dsDoc = reader.findDataSourcesPart().orElseThrow().parseDocument();

        List<DataSourceDefinition> dataSources = new DataSourceXmlParser().parse(dsDoc.getDocumentElement(), new ArrayList<>());

        assertThat(dataSources).hasSize(1);
        assertThat(dataSources.get(0).queries().get(0).name()).isEqualTo("Orders");
    }
}
