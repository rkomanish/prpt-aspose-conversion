package com.migration.prpt2aspose.parser;

import com.migration.prpt2aspose.model.ElementType;
import com.migration.prpt2aspose.model.FieldBinding;
import com.migration.prpt2aspose.model.Geometry;
import com.migration.prpt2aspose.model.ParsingWarning;
import com.migration.prpt2aspose.model.ReportElement;
import com.migration.prpt2aspose.model.StyleDefinition;
import com.migration.prpt2aspose.util.XPathSupport;
import org.w3c.dom.Element;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared geometry/style/binding extraction for every concrete {@link ElementParser}.
 * Subclasses only need to supply the {@link ElementType} they represent and may
 * override {@link #enrich} for type-specific quirks (e.g. image source paths).
 */
abstract class AbstractElementParser implements ElementParser {

    protected abstract ElementType elementType();

    @Override
    public final ReportElement parse(Element xmlElement, String location, List<ParsingWarning> warnings) {
        String name = XPathSupport.attrOrDefault(xmlElement, "name", XPathSupport.localName(xmlElement));
        Geometry geometry = extractGeometry(xmlElement);
        FieldBinding binding = extractBinding(xmlElement);
        StyleDefinition style = extractStyle(xmlElement);
        String text = extractText(xmlElement);

        ReportElement element = new ReportElement(
                name, elementType(), XPathSupport.localName(xmlElement), geometry, binding, style, text);
        return enrich(element, xmlElement, location, warnings);
    }

    /** Extension point for subclasses that need to record extra warnings or post-process the element. Default: no-op. */
    protected ReportElement enrich(ReportElement element, Element xmlElement, String location, List<ParsingWarning> warnings) {
        return element;
    }

    /** Static display text: a "value" attribute wins, else the element's own trimmed text content. */
    private static String extractText(Element el) {
        String value = XPathSupport.attr(el, "value");
        if (value != null) {
            return value;
        }
        String content = el.getTextContent();
        if (content != null && !content.isBlank()) {
            return content.trim();
        }
        return null;
    }

    private static Geometry extractGeometry(Element el) {
        return new Geometry(
                XPathSupport.attrDouble(el, "x", 0),
                XPathSupport.attrDouble(el, "y", 0),
                XPathSupport.attrDouble(el, "width", 0),
                XPathSupport.attrDouble(el, "height", 0));
    }

    /**
     * Pragmatic, defensive binding resolution: a {@code field} attribute starting
     * with "=" is treated as a formula (Pentaho's OpenFormula convention), a plain
     * {@code field} is a direct column reference, and {@code formula}/{@code expression}
     * attributes are recognized explicitly when present instead.
     */
    private static FieldBinding extractBinding(Element el) {
        String field = XPathSupport.attr(el, "field");
        if (field != null && field.startsWith("=")) {
            return FieldBinding.formula(field);
        }
        if (field != null) {
            return FieldBinding.directField(field);
        }
        String formula = XPathSupport.attr(el, "formula");
        if (formula != null) {
            return FieldBinding.formula(formula);
        }
        String expressionRef = XPathSupport.attrOrDefault(el, "expression", XPathSupport.attr(el, "ref"));
        if (expressionRef != null) {
            return FieldBinding.expressionRef(expressionRef);
        }
        return FieldBinding.NONE;
    }

    private static StyleDefinition extractStyle(Element el) {
        String fontFamily = firstNonNull(el, "font-family", "fontFamily", "font-face", "font");
        String fontSizeRaw = firstNonNull(el, "font-size", "fontSize");
        Double fontSize = null;
        if (fontSizeRaw != null) {
            try {
                fontSize = Double.parseDouble(fontSizeRaw.trim());
            } catch (NumberFormatException ignored) {
                // left null; raw value is still preserved via rawAttributes below
            }
        }
        boolean bold = XPathSupport.attrBoolean(el, "bold", false)
                || "bold".equalsIgnoreCase(firstNonNull(el, "font-weight", "fontWeight"));
        boolean italic = XPathSupport.attrBoolean(el, "italic", false)
                || "italic".equalsIgnoreCase(firstNonNull(el, "font-style", "fontStyle"));
        String textAlign = firstNonNull(el, "text-align", "halign", "alignment");
        String backgroundColor = firstNonNull(el, "background-color", "bgcolor", "bg-color");

        return new StyleDefinition(fontFamily, fontSize, bold, italic, textAlign, backgroundColor, allAttributes(el));
    }

    private static String firstNonNull(Element el, String... candidateAttrNames) {
        for (String candidate : candidateAttrNames) {
            String value = XPathSupport.attr(el, candidate);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static Map<String, String> allAttributes(Element el) {
        Map<String, String> attrs = new LinkedHashMap<>();
        var nodeMap = el.getAttributes();
        for (int i = 0; i < nodeMap.getLength(); i++) {
            var node = nodeMap.item(i);
            attrs.put(node.getNodeName(), node.getNodeValue());
        }
        return attrs;
    }
}
