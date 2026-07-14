package com.migration.prpt2aspose.parser;

import com.migration.prpt2aspose.util.SecureXmlSupport;
import com.migration.prpt2aspose.util.XPathSupport;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Opens a .prpt (a ZIP archive) and exposes its member files as {@link XmlPart}s.
 * Report definition files are conventionally named {@code content.xml} or
 * {@code report.xml} depending on Report Designer version; when a
 * {@code META-INF/manifest.xml} is present, it's consulted first (ODF-style
 * manifest, matching on media-type) since that's more reliable than guessing
 * a filename. Either way, a missing/unreadable manifest degrades to filename
 * guessing rather than failing outright.
 */
public final class PrptArchiveReader {

    private static final String MANIFEST_PATH = "META-INF/manifest.xml";
    private static final List<String> REPORT_DEFINITION_CANDIDATES = List.of("content.xml", "report.xml");
    private static final List<String> DATASOURCE_CANDIDATES = List.of("datasources.xml");

    private final Map<String, byte[]> entries;
    private final List<ManifestEntry> manifestEntries;

    private PrptArchiveReader(Map<String, byte[]> entries) {
        this.entries = entries;
        this.manifestEntries = parseManifest(entries.get(MANIFEST_PATH));
    }

    public static PrptArchiveReader open(Path prptFile) throws IOException {
        try (InputStream input = Files.newInputStream(prptFile)) {
            return open(input);
        }
    }

    public static PrptArchiveReader open(InputStream input) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream zis = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    entries.put(entry.getName(), zis.readAllBytes());
                }
                zis.closeEntry();
            }
        }
        return new PrptArchiveReader(entries);
    }

    public Set<String> entryNames() {
        return entries.keySet();
    }

    public boolean hasManifest() {
        return !manifestEntries.isEmpty();
    }

    public Optional<XmlPart> part(String name) {
        byte[] content = entries.get(name);
        return content != null ? Optional.of(new XmlPart(name, content)) : Optional.empty();
    }

    /**
     * The part holding the visual band tree. Real Report-Designer bundles keep
     * it in layout.xml (content.xml is just a pointer there); older/simple
     * exports inline everything into content.xml — so layout.xml wins when present.
     */
    public Optional<XmlPart> findLayoutPart() {
        return part("layout.xml");
    }

    /** Real bundles: parameters, expressions and the primary query name live in datadefinition.xml. */
    public Optional<XmlPart> findDataDefinitionPart() {
        return part("datadefinition.xml");
    }

    /** Real bundles: report title lives in meta.xml (ODF-style dc:title). */
    public Optional<XmlPart> findMetaPart() {
        return part("meta.xml");
    }

    /** Real bundles: each data source is its own file under datasources/ (e.g. datasources/sql-ds.xml). */
    public List<XmlPart> dataSourceFolderParts() {
        return entries.keySet().stream()
                .filter(name -> name.startsWith("datasources/") && name.toLowerCase().endsWith(".xml"))
                .sorted()
                .map(name -> new XmlPart(name, entries.get(name)))
                .toList();
    }

    /**
     * Embedded binary resources (images, logos, Excel templates the legacy
     * report referenced) — everything that isn't report metadata. These get
     * copied next to the conversion outputs so nothing shipped inside the
     * .prpt is lost during migration.
     */
    public Map<String, byte[]> resourceEntries() {
        Map<String, byte[]> resources = new LinkedHashMap<>();
        entries.forEach((name, content) -> {
            String lower = name.toLowerCase();
            boolean metadata = lower.endsWith(".xml")
                    || lower.startsWith("meta-inf/")
                    || lower.equals("mimetype")
                    || lower.endsWith(".properties");
            if (!metadata) {
                resources.put(name, content);
            }
        });
        return resources;
    }

    /** Every .xml member of the archive — used by the structure inspector. */
    public List<XmlPart> allXmlParts() {
        return entries.keySet().stream()
                .filter(name -> name.toLowerCase().endsWith(".xml"))
                .sorted()
                .map(name -> new XmlPart(name, entries.get(name)))
                .toList();
    }

    public long entrySize(String name) {
        byte[] content = entries.get(name);
        return content != null ? content.length : -1;
    }

    /** The master report definition part, resolved via manifest media-type first, then filename guessing. */
    public Optional<XmlPart> findReportDefinitionPart() {
        return findByMediaTypeContaining("reportdefinition")
                .or(() -> findFirstPresent(REPORT_DEFINITION_CANDIDATES));
    }

    /** The standalone datasources part, if this report doesn't inline its data-sources into the report definition. */
    public Optional<XmlPart> findDataSourcesPart() {
        return findByMediaTypeContaining("datasource")
                .or(() -> findFirstPresent(DATASOURCE_CANDIDATES));
    }

    private Optional<XmlPart> findFirstPresent(List<String> candidates) {
        return candidates.stream().map(this::part).flatMap(Optional::stream).findFirst();
    }

    private Optional<XmlPart> findByMediaTypeContaining(String needle) {
        return manifestEntries.stream()
                .filter(e -> e.mediaType().toLowerCase().contains(needle))
                .findFirst()
                .flatMap(e -> part(e.fullPath()));
    }

    private static List<ManifestEntry> parseManifest(byte[] manifestBytes) {
        if (manifestBytes == null) {
            return List.of();
        }
        try {
            Document doc = SecureXmlSupport.parse(new java.io.ByteArrayInputStream(manifestBytes));
            List<Element> fileEntries = XPathSupport.findDescendants(doc, "file-entry");
            return fileEntries.stream()
                    .map(el -> new ManifestEntry(
                            attributeIgnoringPrefix(el, "full-path"),
                            attributeIgnoringPrefix(el, "media-type")))
                    .filter(e -> e.fullPath() != null)
                    .toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    /** manifest.xml conventionally prefixes its attributes (manifest:full-path); match by local name. */
    private static String attributeIgnoringPrefix(Element element, String localName) {
        var attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            var attr = attributes.item(i);
            String name = attr.getLocalName() != null ? attr.getLocalName() : attr.getNodeName();
            if (name.equals(localName)) {
                return attr.getNodeValue();
            }
        }
        return null;
    }

    private record ManifestEntry(String fullPath, String mediaType) {
        private ManifestEntry {
            mediaType = mediaType != null ? mediaType : "";
        }
    }
}
