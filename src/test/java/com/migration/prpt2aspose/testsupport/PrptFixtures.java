package com.migration.prpt2aspose.testsupport;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Builds minimal, synthetic .prpt archives (in memory, no real sample needed)
 * covering the structural features Phase 1 must handle: a query datasource,
 * a group with header/items/footer bands, a parameter, two expressions (one
 * plain formula, one scripted), and one deliberately unrecognized element tag
 * to exercise the tolerant-parsing warning path.
 */
public final class PrptFixtures {

    private PrptFixtures() {
    }

    private static final String CONTENT_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <master-report name="Customer Statement">
                <parameters>
                    <parameter name="fromDate" type="date" default-value="2026-01-01" prompt="From Date" mandatory="true"/>
                </parameters>
                <expressions>
                    <formula-expression name="TotalAmount" formula="=SUM([Amount])"/>
                    <beanshell-expression name="LegacyCalc" formula="return 1;"/>
                </expressions>
                <data-sources>
                    <sql-datasource>
                        <query name="Customers"><![CDATA[SELECT * FROM customers]]></query>
                    </sql-datasource>
                </data-sources>
                <report-header-band>
                    <band>
                        <label name="titleLabel" x="0" y="0" width="200" height="20" bold="true"/>
                    </band>
                </report-header-band>
                <groups>
                    <group name="CustomerGroup">
                        <fields>
                            <field name="CustomerId"/>
                        </fields>
                        <group-header-band>
                            <band>
                                <text-field name="customerNameHeader" field="CustomerName" x="0" y="0" width="100" height="20"/>
                            </band>
                        </group-header-band>
                        <items-band>
                            <band>
                                <text-field name="customerName" field="CustomerName" x="0" y="0" width="100" height="20"/>
                                <number-field name="amount" field="Amount" x="100" y="0" width="80" height="20"/>
                                <weird-custom-element name="mystery" x="0" y="0" width="10" height="10"/>
                            </band>
                        </items-band>
                        <group-footer-band>
                            <band>
                                <label name="footerLabel" x="0" y="0" width="100" height="20"/>
                            </band>
                        </group-footer-band>
                    </group>
                </groups>
                <page-footer-band/>
                <report-footer-band/>
            </master-report>
            """;

    private static final String CONTENT_XML_NO_INLINE_DATASOURCES = CONTENT_XML.replace(
            """
                    <data-sources>
                        <sql-datasource>
                            <query name="Customers"><![CDATA[SELECT * FROM customers]]></query>
                        </sql-datasource>
                    </data-sources>
            """,
            "");

    private static final String DATASOURCES_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <data-sources>
                <sql-datasource>
                    <query name="Orders"><![CDATA[SELECT * FROM orders]]></query>
                </sql-datasource>
            </data-sources>
            """;

    private static final String MANIFEST_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <manifest:manifest xmlns:manifest="urn:oasis:names:tc:opendocument:xmlns:manifest:1.0">
                <manifest:file-entry manifest:full-path="content.xml" manifest:media-type="application/vnd.pentaho.reportdefinition"/>
            </manifest:manifest>
            """;

    /** Full-featured happy-path fixture: manifest present, inline data-sources, one query, one group, one unrecognized element. */
    public static byte[] minimalReportWithManifest() {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("META-INF/manifest.xml", MANIFEST_XML);
        entries.put("content.xml", CONTENT_XML);
        return zipOf(entries);
    }

    /** Same content, but no manifest at all — exercises the filename-guessing fallback. */
    public static byte[] reportWithoutManifest() {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("content.xml", CONTENT_XML);
        return zipOf(entries);
    }

    /** Data-sources live in a standalone datasources.xml instead of being inlined into content.xml. */
    public static byte[] reportWithStandaloneDataSources() {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("META-INF/manifest.xml", MANIFEST_XML);
        entries.put("content.xml", CONTENT_XML_NO_INLINE_DATASOURCES);
        entries.put("datasources.xml", DATASOURCES_XML);
        return zipOf(entries);
    }

    /** A structurally valid but empty archive — no report definition at all. */
    public static byte[] emptyArchive() {
        return zipOf(Map.of());
    }

    private static byte[] zipOf(Map<String, String> entries) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (var entry : entries.entrySet()) {
                zos.putNextEntry(new ZipEntry(entry.getKey()));
                zos.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return baos.toByteArray();
    }
}
