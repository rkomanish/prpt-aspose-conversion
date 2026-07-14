package com.migration.prpt2aspose.parser;

import com.migration.prpt2aspose.model.BandType;
import com.migration.prpt2aspose.model.ExpressionDefinition;
import com.migration.prpt2aspose.model.GroupDefinition;
import com.migration.prpt2aspose.model.ParameterDefinition;
import com.migration.prpt2aspose.model.ParsingWarning;
import com.migration.prpt2aspose.model.ReportBand;
import com.migration.prpt2aspose.model.ReportElement;
import com.migration.prpt2aspose.model.ReportModel;
import com.migration.prpt2aspose.util.XPathSupport;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Extracts parameters, expressions, and the report/page/group band tree from
 * the master report definition part (content.xml / report.xml). Every lookup
 * degrades gracefully: a missing wrapper element (e.g. no {@code <groups>})
 * yields an empty result rather than an exception, consistent with the
 * tolerant-parsing approach described in the architecture plan.
 */
final class ReportXmlParser {

    void populate(Document reportDoc, ReportModel.Builder builder, List<ParsingWarning> warnings) {
        Element root = reportDoc.getDocumentElement();

        builder.reportName(XPathSupport.attrOrDefault(root, "name", "Unnamed Report"));

        parseParameters(root).forEach(builder::addParameter);
        parseExpressions(root).forEach(expression -> {
            builder.addExpression(expression);
            if (expression.isScripted()) {
                warnings.add(ParsingWarning.warning(
                        "SCRIPTED_EXPRESSION", "expressions:" + expression.name(),
                        "Expression '" + expression.name() + "' uses " + expression.language()
                                + " scripting, which Aspose Smart Marker has no direct equivalent for; "
                                + "recommend precomputing this value in the Java service layer or SQL query instead."));
            }
        });

        builder.reportHeaderBand(parseTopBand(root, BandType.REPORT_HEADER, warnings,
                "report-header-band", "reportheader", "report-header"));
        builder.pageHeaderBand(parseTopBand(root, BandType.PAGE_HEADER, warnings,
                "page-header-band", "pageheader", "page-header"));
        builder.pageFooterBand(parseTopBand(root, BandType.PAGE_FOOTER, warnings,
                "page-footer-band", "pagefooter", "page-footer"));
        builder.reportFooterBand(parseTopBand(root, BandType.REPORT_FOOTER, warnings,
                "report-footer-band", "reportfooter", "report-footer"));

        parseGroups(root, warnings).forEach(builder::addGroup);
    }

    private List<ParameterDefinition> parseParameters(Element root) {
        Optional<Element> wrapper = firstChild(root, "parameters");
        if (wrapper.isEmpty()) {
            return List.of();
        }
        List<ParameterDefinition> result = new ArrayList<>();
        for (Element p : XPathSupport.allDirectChildElements(wrapper.get())) {
            String name = XPathSupport.attrOrDefault(p, "name", "param");
            String type = XPathSupport.attrOrDefault(p, "type", XPathSupport.localName(p));
            String defaultValue = firstAttr(p, "default-value", "defaultValue");
            String prompt = firstAttr(p, "prompt", "display-name");
            boolean mandatory = XPathSupport.attrBoolean(p, "mandatory", false);
            result.add(new ParameterDefinition(name, type, defaultValue, prompt, mandatory));
        }
        return result;
    }

    private List<ExpressionDefinition> parseExpressions(Element root) {
        Optional<Element> wrapper = firstChild(root, "expressions");
        if (wrapper.isEmpty()) {
            return List.of();
        }
        List<ExpressionDefinition> result = new ArrayList<>();
        for (Element e : XPathSupport.allDirectChildElements(wrapper.get())) {
            String name = XPathSupport.attrOrDefault(e, "name", "expression");
            String tag = XPathSupport.localName(e);
            String language = resolveLanguage(tag, XPathSupport.attr(e, "language"));
            String body = XPathSupport.attr(e, "formula");
            if (body == null) {
                body = e.getTextContent() != null ? e.getTextContent().trim() : "";
            }
            result.add(new ExpressionDefinition(name, language, body));
        }
        return result;
    }

    private List<GroupDefinition> parseGroups(Element root, List<ParsingWarning> warnings) {
        List<Element> topLevel = childGroupElements(root);
        if (!topLevel.isEmpty()) {
            return topLevel.stream().map(g -> parseGroup(g, warnings)).toList();
        }

        // Flat report: an items band directly under the root (no <group> wrapper)
        // becomes an implicit single "details" group so downstream phases only
        // ever deal with one shape of repeating region.
        Optional<Element> topLevelItems = firstChildOfAny(root, "items-band", "itemband", "details");
        if (topLevelItems.isPresent()) {
            ReportBand itemsBand = parseBandContainer(topLevelItems.get(), BandType.ITEMS, "details:items", warnings);
            return List.of(new GroupDefinition(
                    "details", List.of(),
                    ReportBand.empty(BandType.GROUP_HEADER), itemsBand, ReportBand.empty(BandType.GROUP_FOOTER),
                    List.of()));
        }

        List<Element> anyGroups = XPathSupport.findDescendants(root, "group");
        if (anyGroups.isEmpty()) {
            return List.of();
        }
        warnings.add(ParsingWarning.info(
                "GROUP_STRUCTURE_ASSUMED", "report",
                "No <groups> wrapper or top-level <group> found directly under the report root; "
                        + "falling back to scanning the whole document for <group> elements, "
                        + "so multi-level nesting may not be resolved precisely."));
        return anyGroups.stream()
                .filter(g -> !isNestedInsideAnotherGroup(g, root))
                .map(g -> parseGroup(g, warnings))
                .toList();
    }

    private GroupDefinition parseGroup(Element groupEl, List<ParsingWarning> warnings) {
        String name = XPathSupport.attrOrDefault(groupEl, "name", "group");

        List<String> fields = new ArrayList<>();
        for (Element fieldsWrapper : XPathSupport.findDirectChildren(groupEl, "fields")) {
            for (Element f : XPathSupport.findDirectChildren(fieldsWrapper, "field")) {
                String fieldName = XPathSupport.attr(f, "name");
                if (fieldName == null && f.getTextContent() != null) {
                    fieldName = f.getTextContent().trim();
                }
                if (fieldName != null && !fieldName.isBlank()) {
                    fields.add(fieldName);
                }
            }
        }

        ReportBand headerBand = firstChildOfAny(groupEl, "group-header-band", "group-header")
                .map(el -> parseBandContainer(el, BandType.GROUP_HEADER, "group:" + name + ":header", warnings))
                .orElse(ReportBand.empty(BandType.GROUP_HEADER));
        ReportBand footerBand = firstChildOfAny(groupEl, "group-footer-band", "group-footer")
                .map(el -> parseBandContainer(el, BandType.GROUP_FOOTER, "group:" + name + ":footer", warnings))
                .orElse(ReportBand.empty(BandType.GROUP_FOOTER));

        // Real bundles wrap the repeating content in <group-body>; header/footer stay at group level.
        Element itemsScope = firstChild(groupEl, "group-body").orElse(groupEl);
        Optional<Element> itemsBandEl = firstChildOfAny(itemsScope, "items-band", "itemband", "details");
        if (itemsBandEl.isEmpty() && itemsScope != groupEl) {
            itemsBandEl = firstChildOfAny(groupEl, "items-band", "itemband", "details");
        }
        List<Element> nestedGroups = childGroupElements(itemsScope);
        if (nestedGroups.isEmpty() && itemsScope != groupEl) {
            nestedGroups = childGroupElements(groupEl);
        }

        ReportBand itemsBand;
        List<GroupDefinition> subGroups;
        if (itemsBandEl.isPresent()) {
            itemsBand = parseBandContainer(itemsBandEl.get(), BandType.ITEMS, "group:" + name + ":items", warnings);
            subGroups = List.of();
        } else if (!nestedGroups.isEmpty()) {
            itemsBand = ReportBand.empty(BandType.ITEMS);
            subGroups = nestedGroups.stream().map(sg -> parseGroup(sg, warnings)).toList();
        } else {
            warnings.add(ParsingWarning.warning(
                    "GROUP_MISSING_ITEMS_BAND", "group:" + name,
                    "Group '" + name + "' has neither an <items-band> nor nested subgroups; "
                            + "no repeating Smart Marker region will be generated for it."));
            itemsBand = ReportBand.empty(BandType.ITEMS);
            subGroups = List.of();
        }

        return new GroupDefinition(name, fields, headerBand, itemsBand, footerBand, subGroups);
    }

    private ReportBand parseTopBand(Element root, BandType type, List<ParsingWarning> warnings, String... tagCandidates) {
        return firstChildOfAny(root, tagCandidates)
                .map(el -> parseBandContainer(el, type, tagCandidates[0], warnings))
                .orElse(ReportBand.empty(type));
    }

    /**
     * Bands conventionally wrap their real content in a single nested {@code <band>}
     * element; when present we descend into it, otherwise we treat the band element
     * itself as the content container (schemas do vary here across PRD versions).
     */
    private ReportBand parseBandContainer(Element outer, BandType type, String location, List<ParsingWarning> warnings) {
        Element content = XPathSupport.findDirectChildren(outer, "band").stream().findFirst().orElse(outer);
        List<ReportElement> elements = new ArrayList<>();
        collectBandElements(content, location, elements, warnings);
        return new ReportBand(type, elements);
    }

    /** Band metadata children that are not visual elements and must not reach the element parsers. */
    private static final java.util.Set<String> NON_VISUAL_BAND_CHILDREN = java.util.Set.of(
            "style", "attributes", "style-expression", "style-expressions",
            "attribute-expressions", "expression", "layout-processor", "no-data-band");

    /**
     * Flattens a band's children into positioned elements. Nested {@code <band>}
     * containers (bundle format nests freely) are recursed into rather than
     * dropped, and structured bundle elements are normalized to the flat
     * attribute form first.
     */
    private void collectBandElements(Element container, String location,
                                     List<ReportElement> out, List<ParsingWarning> warnings) {
        for (Element child : XPathSupport.allDirectChildElements(container)) {
            String tag = XPathSupport.localName(child);
            if ("band".equals(tag)) {
                collectBandElements(child, location, out, warnings);
                continue;
            }
            if (NON_VISUAL_BAND_CHILDREN.contains(tag)) {
                continue;
            }
            Element parseTarget = BundleElementNormalizer.needsNormalization(child)
                    ? BundleElementNormalizer.normalize(child)
                    : child;
            ElementParser parser = ElementParserFactory.forTag(BundleElementNormalizer.resolveTag(child));
            out.add(parser.parse(parseTarget, location, warnings));
        }
    }

    /**
     * Direct child group elements, whether declared directly or inside a
     * <groups> wrapper. Real bundles use <relational-group>/<crosstab-group>
     * instead of plain <group>.
     */
    private List<Element> childGroupElements(Element parent) {
        List<Element> wrapper = XPathSupport.findDirectChildren(parent, "groups");
        Element scope = wrapper.isEmpty() ? parent : wrapper.get(0);
        return XPathSupport.allDirectChildElements(scope).stream()
                .filter(el -> {
                    String tag = XPathSupport.localName(el);
                    return "group".equals(tag) || "relational-group".equals(tag) || "crosstab-group".equals(tag);
                })
                .toList();
    }

    private boolean isNestedInsideAnotherGroup(Element element, Element root) {
        org.w3c.dom.Node current = element.getParentNode();
        while (current != null && current != root) {
            if (current instanceof Element el && "group".equals(XPathSupport.localName(el))) {
                return true;
            }
            current = current.getParentNode();
        }
        return false;
    }

    private static Optional<Element> firstChild(Element parent, String tag) {
        return XPathSupport.findDirectChildren(parent, tag).stream().findFirst();
    }

    private static Optional<Element> firstChildOfAny(Element parent, String... tags) {
        for (String tag : tags) {
            Optional<Element> found = firstChild(parent, tag);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    private static String firstAttr(Element el, String... candidates) {
        for (String candidate : candidates) {
            String value = XPathSupport.attr(el, candidate);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String resolveLanguage(String tag, String explicit) {
        if (explicit != null) {
            return explicit;
        }
        String lower = tag.toLowerCase();
        if (lower.contains("beanshell")) {
            return "beanshell";
        }
        if (lower.contains("javascript")) {
            return "javascript";
        }
        if (lower.contains("formula")) {
            return "formula";
        }
        return "unknown";
    }
}
