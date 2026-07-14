package com.migration.prpt2aspose.parser;

import com.migration.prpt2aspose.util.XPathSupport;
import org.w3c.dom.Element;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Real Report-Designer bundles serialize a band child not as a flat tag with
 * attributes but as a structured node:
 *
 * <pre>
 *   &lt;element metadata-name="text-field"&gt;          (or a namespaced concrete tag)
 *     &lt;style&gt;&lt;min-x&gt;0&lt;/min-x&gt;&lt;min-width&gt;100&lt;/min-width&gt;&lt;font-bold&gt;true&lt;/font-bold&gt;...&lt;/style&gt;
 *     &lt;attributes&gt;&lt;core:field&gt;CUSTOMER_NAME&lt;/core:field&gt;...&lt;/attributes&gt;
 *   &lt;/element&gt;
 * </pre>
 *
 * This normalizer folds all three property sources (XML attributes, style
 * children, attributes children) into one flat synthetic element with the
 * canonical attribute names the {@link ElementParser}s already understand
 * (x/y/width/height/field/value/font-face/bold/...), so the entire downstream
 * pipeline is format-agnostic. Nothing is lost: every collected property is
 * copied onto the synthetic element and therefore lands in rawAttributes.
 */
final class BundleElementNormalizer {

    /** Canonical name -> accepted aliases, in priority order. */
    private static final Map<String, List<String>> CANONICAL_ALIASES = Map.ofEntries(
            Map.entry("x", List.of("x", "min-x", "pos-x")),
            Map.entry("y", List.of("y", "min-y", "pos-y")),
            Map.entry("width", List.of("width", "min-width", "preferred-width", "max-width")),
            Map.entry("height", List.of("height", "min-height", "preferred-height", "max-height")),
            Map.entry("field", List.of("field")),
            Map.entry("value", List.of("value", "text")),
            Map.entry("formula", List.of("formula")),
            Map.entry("name", List.of("name")),
            Map.entry("font-face", List.of("font-face", "font-name", "font-family", "font")),
            Map.entry("font-size", List.of("font-size")),
            Map.entry("bold", List.of("bold", "font-bold")),
            Map.entry("italic", List.of("italic", "font-italic")),
            Map.entry("alignment", List.of("alignment", "text-alignment", "horizontal-alignment", "text-align", "halign")),
            Map.entry("bg-color", List.of("bg-color", "background-color", "bgcolor", "fill-color")),
            Map.entry("format", List.of("format", "format-string")),
            Map.entry("src", List.of("src", "content-base", "resource-identifier")));

    /** Bundle metadata-names / legacy tags -> the tag names ElementParserFactory knows. */
    private static final Map<String, String> TAG_ALIASES = Map.ofEntries(
            Map.entry("string-field", "text-field"),
            Map.entry("message", "label"),
            Map.entry("resource-label", "label"),
            Map.entry("resource-message", "label"),
            Map.entry("horizontal-line", "line"),
            Map.entry("vertical-line", "line"),
            Map.entry("round-rectangle", "rectangle"),
            Map.entry("image", "image-field"),
            Map.entry("content", "image-field"),
            Map.entry("content-field", "image-field"),
            Map.entry("big-decimal-field", "number-field"),
            Map.entry("int-field", "number-field"));

    private BundleElementNormalizer() {
    }

    /** True when this band child uses the structured bundle form and needs normalizing before parsing. */
    static boolean needsNormalization(Element el) {
        return "element".equals(XPathSupport.localName(el))
                || !XPathSupport.findDirectChildren(el, "style").isEmpty()
                || !XPathSupport.findDirectChildren(el, "attributes").isEmpty();
    }

    /** Resolved canonical tag name for a band child (works for both flat and bundle forms). */
    static String resolveTag(Element el) {
        String tag = XPathSupport.localName(el);
        if ("element".equals(tag)) {
            String metadataName = firstAttrByLocalName(el, "metadata-name", "type");
            tag = metadataName != null ? metadataName : "element";
        }
        // metadata-name can be namespace-qualified, e.g. ".../classic/core::label"
        int separator = Math.max(tag.lastIndexOf(':'), Math.max(tag.lastIndexOf('/'), tag.lastIndexOf('#')));
        if (separator >= 0) {
            tag = tag.substring(separator + 1);
        }
        return TAG_ALIASES.getOrDefault(tag, tag);
    }

    /**
     * Builds the flat synthetic element. All collected properties are copied as
     * attributes; canonical names are then overlaid from their alias lists.
     */
    static Element normalize(Element el) {
        Map<String, String> props = collectProperties(el);

        Element synthetic = el.getOwnerDocument().createElement(resolveTag(el));
        props.forEach((key, value) -> {
            if (isValidAttributeName(key)) {
                synthetic.setAttribute(key, value);
            }
        });
        CANONICAL_ALIASES.forEach((canonical, aliases) -> {
            for (String alias : aliases) {
                String value = props.get(alias);
                if (value != null && !value.isBlank()) {
                    synthetic.setAttribute(canonical, value);
                    break;
                }
            }
        });
        return synthetic;
    }

    /** XML attributes first, then <style> children, then <attributes> children (both element-child and <attribute name=..> forms). */
    private static Map<String, String> collectProperties(Element el) {
        Map<String, String> props = new LinkedHashMap<>();

        var attrs = el.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            var attr = attrs.item(i);
            String local = attr.getLocalName() != null ? attr.getLocalName() : attr.getNodeName();
            props.putIfAbsent(local, attr.getNodeValue());
        }

        for (Element container : XPathSupport.findDirectChildren(el, "style")) {
            harvestChildren(container, props);
        }
        for (Element container : XPathSupport.findDirectChildren(el, "attributes")) {
            harvestChildren(container, props);
        }
        return props;
    }

    private static void harvestChildren(Element container, Map<String, String> props) {
        for (Element child : XPathSupport.allDirectChildElements(container)) {
            String local = XPathSupport.localName(child);
            String value;
            if ("attribute".equals(local)) {
                // <attribute namespace="..." name="value">text</attribute>
                local = firstAttrByLocalName(child, "name");
                value = child.getTextContent();
            } else {
                value = child.getTextContent();
            }
            if (local != null && value != null && !value.isBlank()) {
                props.putIfAbsent(local, value.trim());
            }
        }
    }

    private static String firstAttrByLocalName(Element el, String... names) {
        var attrs = el.getAttributes();
        for (String wanted : names) {
            for (int i = 0; i < attrs.getLength(); i++) {
                var attr = attrs.item(i);
                String local = attr.getLocalName() != null ? attr.getLocalName() : attr.getNodeName();
                if (wanted.equals(local)) {
                    return attr.getNodeValue();
                }
            }
        }
        return null;
    }

    private static boolean isValidAttributeName(String name) {
        return name != null && name.matches("[A-Za-z_][A-Za-z0-9._-]*");
    }
}
