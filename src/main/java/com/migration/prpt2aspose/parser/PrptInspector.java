package com.migration.prpt2aspose.parser;

import com.migration.prpt2aspose.util.XPathSupport;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Diagnostic dump of a .prpt archive's internal structure ({@code --inspect}).
 * When a report converts to fewer cells than expected, this output shows
 * exactly which parts/tags the file uses so the parser can be calibrated —
 * paste it into an issue instead of sharing the (possibly confidential) report.
 */
@Component
public final class PrptInspector {

    public String inspect(Path prptFile) {
        StringBuilder out = new StringBuilder();
        out.append("=== PRPT structure: ").append(prptFile.getFileName()).append(" ===\n");
        try {
            PrptArchiveReader archive = PrptArchiveReader.open(prptFile);

            out.append("\n-- Archive entries --\n");
            for (String entry : archive.entryNames()) {
                out.append(String.format("  %-50s %8d bytes%n", entry, archive.entrySize(entry)));
            }

            for (XmlPart part : archive.allXmlParts()) {
                out.append("\n-- ").append(part.name()).append(" --\n");
                try {
                    Document doc = part.parseDocument();
                    Element root = doc.getDocumentElement();
                    out.append("  root: <").append(XPathSupport.localName(root)).append(">")
                            .append(root.getNamespaceURI() != null ? "  ns=" + root.getNamespaceURI() : "")
                            .append("\n");
                    rootAttributes(root, out);
                    childHistogram(root, out);
                    interestingCounts(root, out);
                    sampleElements(part, doc, out);
                } catch (Exception e) {
                    out.append("  (unparseable: ").append(e.getMessage()).append(")\n");
                }
            }
        } catch (Exception e) {
            out.append("Failed to open archive: ").append(e.getMessage()).append("\n");
        }
        return out.toString();
    }

    /**
     * For the layout part, serialize the first two visual elements verbatim —
     * the single most useful calibration datum, since it shows exactly how this
     * Report-Designer version encodes geometry, styles and field bindings.
     */
    private void sampleElements(XmlPart part, Document doc, StringBuilder out) {
        if (!part.name().contains("layout") && !part.name().contains("content")) {
            return;
        }
        List<Element> samples = XPathSupport.findDescendants(doc.getDocumentElement(), "element");
        if (samples.isEmpty()) {
            for (String tag : List.of("label", "text-field", "number-field")) {
                samples = XPathSupport.findDescendants(doc.getDocumentElement(), tag);
                if (!samples.isEmpty()) {
                    break;
                }
            }
        }
        for (int i = 0; i < Math.min(2, samples.size()); i++) {
            out.append("  sample element ").append(i + 1).append(":\n");
            out.append(indent(serialize(samples.get(i)), "    ")).append("\n");
        }
    }

    private String serialize(Element element) {
        try {
            var transformer = javax.xml.transform.TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(javax.xml.transform.OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformer.setOutputProperty(javax.xml.transform.OutputKeys.INDENT, "yes");
            var writer = new java.io.StringWriter();
            transformer.transform(
                    new javax.xml.transform.dom.DOMSource(element),
                    new javax.xml.transform.stream.StreamResult(writer));
            String xml = writer.toString();
            return xml.length() > 2500 ? xml.substring(0, 2500) + "\n... (truncated)" : xml;
        } catch (Exception e) {
            return "(serialization failed: " + e.getMessage() + ")";
        }
    }

    private String indent(String text, String prefix) {
        return prefix + text.replace("\n", "\n" + prefix);
    }

    private void rootAttributes(Element root, StringBuilder out) {
        var attrs = root.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            var attr = attrs.item(i);
            if (!attr.getNodeName().startsWith("xmlns")) {
                out.append("  root@").append(attr.getNodeName()).append(" = ").append(attr.getNodeValue()).append("\n");
            }
        }
    }

    private void childHistogram(Element root, StringBuilder out) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Element child : XPathSupport.allDirectChildElements(root)) {
            counts.merge(XPathSupport.localName(child), 1, Integer::sum);
        }
        if (!counts.isEmpty()) {
            out.append("  direct children: ").append(counts).append("\n");
        }
    }

    private void interestingCounts(Element root, StringBuilder out) {
        List<String> interesting = List.of(
                "query", "parameter-definition", "plain-parameter", "expression",
                "element", "band", "itemband", "relational-group", "group",
                "report-header", "page-header", "group-header", "label", "text-field", "number-field", "date-field");
        StringBuilder line = new StringBuilder();
        for (String tag : interesting) {
            int count = XPathSupport.findDescendants(root, tag).size();
            if (count > 0) {
                line.append(tag).append("=").append(count).append(" ");
            }
        }
        if (!line.isEmpty()) {
            out.append("  descendants: ").append(line).append("\n");
        }
    }
}
