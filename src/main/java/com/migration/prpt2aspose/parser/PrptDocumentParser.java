package com.migration.prpt2aspose.parser;

import com.migration.prpt2aspose.model.DataSourceDefinition;
import com.migration.prpt2aspose.model.ParsingWarning;
import com.migration.prpt2aspose.model.QueryDefinition;
import com.migration.prpt2aspose.model.ReportModel;
import com.migration.prpt2aspose.util.XPathSupport;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Orchestrates archive reading + part parsing into one {@link ReportModel}.
 * Two archive layouts are supported transparently:
 *
 * <ul>
 *   <li><b>Real Report-Designer bundles</b>: bands in layout.xml, parameters/
 *       expressions/primary-query in datadefinition.xml, one file per data
 *       source under datasources/, title in meta.xml.</li>
 *   <li><b>Single-document exports</b>: everything inlined into content.xml /
 *       report.xml (master-report root).</li>
 * </ul>
 */
@Component
public final class PrptDocumentParser implements PrptParser {

    private final ReportXmlParser reportXmlParser = new ReportXmlParser();
    private final DataSourceXmlParser dataSourceXmlParser = new DataSourceXmlParser();
    private final DataDefinitionParser dataDefinitionParser = new DataDefinitionParser();

    @Override
    public ReportModel parse(Path prptFile) {
        try (InputStream input = Files.newInputStream(prptFile)) {
            return parse(input, prptFile.getFileName().toString());
        } catch (IOException e) {
            throw new PrptParsingException("Unable to read PRPT file: " + prptFile, e);
        }
    }

    @Override
    public ReportModel parse(InputStream input, String sourceName) {
        List<ParsingWarning> warnings = new ArrayList<>();
        ReportModel.Builder builder = ReportModel.builder();

        try {
            PrptArchiveReader archive = PrptArchiveReader.open(input);
            if (!archive.hasManifest()) {
                warnings.add(ParsingWarning.info(
                        "MANIFEST_MISSING", sourceName,
                        "No META-INF/manifest.xml found; falling back to filename-based part resolution."));
            }

            boolean bundleFormat = archive.findLayoutPart().isPresent();
            Optional<XmlPart> bandsPart = archive.findLayoutPart()
                    .or(archive::findReportDefinitionPart);
            if (bandsPart.isEmpty()) {
                throw new PrptParsingException(
                        "No report definition (layout.xml/content.xml/report.xml) found inside " + sourceName);
            }
            if (bundleFormat) {
                warnings.add(ParsingWarning.info(
                        "BUNDLE_FORMAT", sourceName,
                        "Detected real Report-Designer bundle layout (layout.xml + datadefinition.xml)."));
            }

            Document bandsDoc = bandsPart.get().parseDocument();
            reportXmlParser.populate(bandsDoc, builder, warnings);

            resolveReportName(archive, sourceName).ifPresent(builder::reportName);

            Optional<String> primaryQuery = parseDataDefinition(archive, builder, warnings);

            List<DataSourceDefinition> dataSources = resolveDataSources(archive, bandsDoc, warnings);
            if (dataSources.isEmpty()) {
                warnings.add(ParsingWarning.warning(
                        "NO_DATASOURCES_FOUND", sourceName,
                        "No data sources/queries were found in this report; if it truly has none this is expected, "
                                + "otherwise run with --inspect to see the archive structure."));
            }
            primaryQueryFirst(dataSources, primaryQuery).forEach(builder::addDataSource);

            builder.addWarnings(warnings);
            return builder.build();
        } catch (PrptParsingException e) {
            throw e;
        } catch (Exception e) {
            throw new PrptParsingException("Failed to parse PRPT file " + sourceName, e);
        }
    }

    /** Real bundles: dc:title inside meta.xml. Single-document exports already got the name from the master-report attr. */
    private Optional<String> resolveReportName(PrptArchiveReader archive, String sourceName) {
        return archive.findMetaPart().flatMap(part -> {
            try {
                Document metaDoc = part.parseDocument();
                return XPathSupport.findDescendants(metaDoc.getDocumentElement(), "title").stream()
                        .map(el -> el.getTextContent() != null ? el.getTextContent().trim() : "")
                        .filter(title -> !title.isBlank())
                        .findFirst();
            } catch (Exception e) {
                return Optional.empty();
            }
        });
    }

    private Optional<String> parseDataDefinition(
            PrptArchiveReader archive, ReportModel.Builder builder, List<ParsingWarning> warnings) {
        Optional<XmlPart> part = archive.findDataDefinitionPart();
        if (part.isEmpty()) {
            return Optional.empty();
        }
        try {
            DataDefinitionParser.Result result = dataDefinitionParser.parse(part.get().parseDocument());
            result.parameters().forEach(builder::addParameter);
            result.expressions().forEach(expression -> {
                builder.addExpression(expression);
                if (expression.isScripted()) {
                    warnings.add(ParsingWarning.warning(
                            "SCRIPTED_EXPRESSION", "expressions:" + expression.name(),
                            "Expression '" + expression.name() + "' uses " + expression.language()
                                    + " scripting, which Aspose Smart Marker has no direct equivalent for; "
                                    + "recommend precomputing this value in the Java service layer or SQL query instead."));
                }
            });
            return result.primaryQueryName();
        } catch (Exception e) {
            warnings.add(ParsingWarning.warning(
                    "DATADEFINITION_UNPARSEABLE", "datadefinition.xml",
                    "datadefinition.xml exists but could not be parsed: " + e.getMessage()));
            return Optional.empty();
        }
    }

    private List<DataSourceDefinition> resolveDataSources(
            PrptArchiveReader archive, Document bandsDoc, List<ParsingWarning> warnings) throws Exception {
        List<DataSourceDefinition> result = new ArrayList<>();

        // Real bundles: one file per data source under datasources/.
        for (XmlPart part : archive.dataSourceFolderParts()) {
            try {
                result.addAll(dataSourceXmlParser.parse(part.parseDocument().getDocumentElement(), warnings));
            } catch (Exception e) {
                warnings.add(ParsingWarning.warning(
                        "DATASOURCE_PART_UNPARSEABLE", part.name(),
                        "Data source part could not be parsed: " + e.getMessage()));
            }
        }
        if (!result.isEmpty()) {
            return result;
        }

        // Standalone datasources.xml, else inline <data-sources> in the report definition.
        Optional<XmlPart> standalone = archive.findDataSourcesPart();
        if (standalone.isPresent()) {
            return dataSourceXmlParser.parse(standalone.get().parseDocument().getDocumentElement(), warnings);
        }
        return dataSourceXmlParser.parse(bandsDoc.getDocumentElement(), warnings);
    }

    /**
     * The primary query (datadefinition.xml root's {@code query} attribute) is
     * what the item bands repeat over — moving it first makes it the dataset
     * the Smart Marker generator binds repeating regions to.
     */
    private List<DataSourceDefinition> primaryQueryFirst(
            List<DataSourceDefinition> dataSources, Optional<String> primaryQuery) {
        if (primaryQuery.isEmpty()) {
            return dataSources;
        }
        String primary = primaryQuery.get();
        return dataSources.stream()
                .map(ds -> {
                    List<QueryDefinition> reordered = new ArrayList<>(ds.queries());
                    reordered.sort(Comparator.comparing(q -> q.name().equals(primary) ? 0 : 1));
                    return new DataSourceDefinition(ds.type(), reordered);
                })
                .sorted(Comparator.comparing(
                        ds -> ds.queries().stream().anyMatch(q -> q.name().equals(primary)) ? 0 : 1))
                .toList();
    }
}
