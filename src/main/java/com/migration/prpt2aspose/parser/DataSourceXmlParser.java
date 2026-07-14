package com.migration.prpt2aspose.parser;

import com.migration.prpt2aspose.model.DataSourceDefinition;
import com.migration.prpt2aspose.model.ParsingWarning;
import com.migration.prpt2aspose.model.QueryDefinition;
import com.migration.prpt2aspose.util.XPathSupport;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Extracts named SQL queries from either a standalone datasources.xml part or
 * an inline {@code <data-sources>} block within the report definition — this
 * is the source of truth for the eventual {@code queries.sql} artifact.
 * Non-SQL data source types (Kettle transformations, MQL, MDX) are recorded
 * as a warning rather than silently dropped, since they need manual migration.
 */
final class DataSourceXmlParser {

    List<DataSourceDefinition> parse(Element searchRoot, List<ParsingWarning> warnings) {
        List<Element> dataSourceElements = resolveDataSourceElements(searchRoot);
        if (dataSourceElements.isEmpty()) {
            return List.of();
        }

        List<DataSourceDefinition> result = new ArrayList<>();
        for (Element dsEl : dataSourceElements) {
            String type = XPathSupport.localName(dsEl);
            List<QueryDefinition> queries = new ArrayList<>();

            for (Element q : XPathSupport.findDescendants(dsEl, "query")) {
                String queryName = XPathSupport.attrOrDefault(q, "name", "query");
                String sql = q.getTextContent() != null ? q.getTextContent().trim() : "";
                if (sql.isBlank()) {
                    warnings.add(ParsingWarning.warning(
                            "EMPTY_QUERY", "datasource:" + type + ":" + queryName,
                            "Query '" + queryName + "' has no SQL body."));
                }
                queries.add(new QueryDefinition(queryName, sql));
            }

            if (queries.isEmpty()) {
                warnings.add(ParsingWarning.info(
                        "UNSUPPORTED_DATASOURCE", "datasource:" + type,
                        "Data source of type '" + type + "' has no recognizable <query> elements; "
                                + "if this is a non-SQL source (Kettle transformation, MQL, MDX), "
                                + "its query logic needs manual migration."));
            }

            result.add(new DataSourceDefinition(type, queries));
        }
        return result;
    }

    /**
     * Three shapes are accepted: a {@code <data-sources>} wrapper (each child is
     * one data source), a document whose root itself IS the data source (real
     * bundles: datasources/sql-ds.xml with an {@code <sql-datasource>} root),
     * or a report definition containing a {@code <data-sources>} block somewhere.
     */
    private List<Element> resolveDataSourceElements(Element searchRoot) {
        String rootTag = XPathSupport.localName(searchRoot);
        if ("data-sources".equals(rootTag)) {
            return XPathSupport.allDirectChildElements(searchRoot);
        }
        if (rootTag.contains("datasource") || rootTag.contains("data-source")) {
            return List.of(searchRoot);
        }
        Optional<Element> wrapper = XPathSupport.findDirectChildren(searchRoot, "data-sources").stream().findFirst()
                .or(() -> XPathSupport.findDescendants(searchRoot, "data-sources").stream().findFirst());
        return wrapper.map(XPathSupport::allDirectChildElements).orElse(List.of());
    }
}
