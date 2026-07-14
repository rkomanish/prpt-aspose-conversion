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
 * Builds a .prpt mimicking the REAL Report-Designer bundle layout (as seen in
 * the user's MonthlyBorrowerOverviewReport.prpt): namespaced layout.xml with
 * structured &lt;element metadata-name=...&gt; nodes, datadefinition.xml with the
 * primary query attribute, one data source file under datasources/, the title
 * in meta.xml, and an embedded binary resource.
 */
public final class PrptBundleFixtures {

    private PrptBundleFixtures() {
    }

    private static final String MANIFEST_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <manifest:manifest xmlns:manifest="urn:oasis:names:tc:opendocument:xmlns:manifest:1.0">
                <manifest:file-entry manifest:full-path="content.xml" manifest:media-type="text/xml"/>
                <manifest:file-entry manifest:full-path="layout.xml" manifest:media-type="text/xml"/>
                <manifest:file-entry manifest:full-path="datadefinition.xml" manifest:media-type="text/xml"/>
            </manifest:manifest>
            """;

    /** Real bundles: content.xml is only a pointer, not the report. */
    private static final String CONTENT_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <content xmlns="http://reporting.pentaho.org/namespaces/engine/classic/bundle/content/1.0"
                     layout="layout.xml"/>
            """;

    private static final String META_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <office:document-meta xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
                                  xmlns:dc="http://purl.org/dc/elements/1.1/">
                <office:meta>
                    <dc:title>Monthly Borrower Overview</dc:title>
                    <dc:creator>legacy-team</dc:creator>
                </office:meta>
            </office:document-meta>
            """;

    private static final String DATADEFINITION_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <data-definition xmlns="http://reporting.pentaho.org/namespaces/engine/classic/bundle/datadefinition/1.0"
                             limit="-1" query="BorrowerQuery" query-timeout="0">
                <parameter-definition>
                    <plain-parameter name="asOfDate" type="java.util.Date" mandatory="true">
                        <default-value>2026-06-30</default-value>
                    </plain-parameter>
                </parameter-definition>
                <expression class="org.pentaho.reporting.engine.classic.core.function.FormulaExpression" name="TotalExposure">
                    <properties>
                        <property name="formula">=SUM([EXPOSURE_AMT])</property>
                    </properties>
                </expression>
            </data-definition>
            """;

    private static final String SQL_DS_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <sql-datasource xmlns="http://reporting.pentaho.org/namespaces/datasources/sql">
                <config label-mapping="true"/>
                <query-definitions>
                    <query name="BorrowerQuery">
                        <static-query><![CDATA[
            SELECT borrower_name AS BORROWER_NAME,
                   sector        AS SECTOR,
                   exposure_amt  AS EXPOSURE_AMT
            FROM   borrower_overview
            WHERE  as_of_date = ${asOfDate}
            ORDER  BY sector, borrower_name
            ]]></static-query>
                    </query>
                </query-definitions>
            </sql-datasource>
            """;

    private static final String LAYOUT_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <layout xmlns="http://reporting.pentaho.org/namespaces/engine/classic/bundle/layout/1.0"
                    xmlns:core="http://reporting.pentaho.org/namespaces/engine/attributes/core">
                <report-header>
                    <element metadata-name="label">
                        <style>
                            <min-x>0</min-x>
                            <min-y>0</min-y>
                            <min-width>300</min-width>
                            <min-height>24</min-height>
                            <font-name>Arial</font-name>
                            <font-size>15</font-size>
                            <font-bold>true</font-bold>
                        </style>
                        <attributes>
                            <core:value>Monthly Borrower Overview</core:value>
                            <core:name>titleLabel</core:name>
                        </attributes>
                    </element>
                </report-header>
                <relational-group name="SectorGroup">
                    <fields>
                        <field>SECTOR</field>
                    </fields>
                    <group-header>
                        <element metadata-name="text-field">
                            <style>
                                <min-x>0</min-x>
                                <min-width>300</min-width>
                                <min-height>18</min-height>
                                <font-bold>true</font-bold>
                                <bg-color>#EEEEEE</bg-color>
                            </style>
                            <attributes>
                                <core:field>SECTOR</core:field>
                                <core:name>sectorHeader</core:name>
                            </attributes>
                        </element>
                    </group-header>
                    <group-body>
                        <itemband>
                            <element metadata-name="text-field">
                                <style>
                                    <min-x>0</min-x>
                                    <min-width>180</min-width>
                                    <min-height>16</min-height>
                                </style>
                                <attributes>
                                    <core:field>BORROWER_NAME</core:field>
                                    <core:name>borrowerName</core:name>
                                </attributes>
                            </element>
                            <element metadata-name="number-field">
                                <style>
                                    <min-x>180</min-x>
                                    <min-width>120</min-width>
                                    <min-height>16</min-height>
                                    <text-alignment>right</text-alignment>
                                </style>
                                <attributes>
                                    <core:field>EXPOSURE_AMT</core:field>
                                    <core:format-string>#,##0.00</core:format-string>
                                    <core:name>exposureAmt</core:name>
                                </attributes>
                            </element>
                        </itemband>
                    </group-body>
                    <group-footer>
                        <element metadata-name="message">
                            <style>
                                <min-x>0</min-x>
                                <min-width>180</min-width>
                                <min-height>16</min-height>
                                <font-bold>true</font-bold>
                            </style>
                            <attributes>
                                <core:value>Sector total:</core:value>
                                <core:name>sectorTotalLabel</core:name>
                            </attributes>
                        </element>
                    </group-footer>
                </relational-group>
            </layout>
            """;

    public static byte[] realisticBundle() {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("mimetype", "application/vnd.pentaho.reportdefinition".getBytes(StandardCharsets.UTF_8));
        entries.put("META-INF/manifest.xml", MANIFEST_XML.getBytes(StandardCharsets.UTF_8));
        entries.put("content.xml", CONTENT_XML.getBytes(StandardCharsets.UTF_8));
        entries.put("layout.xml", LAYOUT_XML.getBytes(StandardCharsets.UTF_8));
        entries.put("datadefinition.xml", DATADEFINITION_XML.getBytes(StandardCharsets.UTF_8));
        entries.put("meta.xml", META_XML.getBytes(StandardCharsets.UTF_8));
        entries.put("datasources/sql-ds.xml", SQL_DS_XML.getBytes(StandardCharsets.UTF_8));
        entries.put("translations.properties", "title=Monthly Borrower Overview".getBytes(StandardCharsets.UTF_8));
        // stand-in for the embedded Excel template seen in the real file
        entries.put("embedded-template.xlsx", new byte[]{0x50, 0x4B, 0x03, 0x04, 0x00, 0x00});
        return zipOf(entries);
    }

    private static byte[] zipOf(Map<String, byte[]> entries) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (var entry : entries.entrySet()) {
                zos.putNextEntry(new ZipEntry(entry.getKey()));
                zos.write(entry.getValue());
                zos.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return baos.toByteArray();
    }
}
