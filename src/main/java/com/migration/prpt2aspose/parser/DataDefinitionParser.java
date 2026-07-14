package com.migration.prpt2aspose.parser;

import com.migration.prpt2aspose.model.ExpressionDefinition;
import com.migration.prpt2aspose.model.ParameterDefinition;
import com.migration.prpt2aspose.util.XPathSupport;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Parses a real bundle's datadefinition.xml: the primary query name (the
 * {@code query} attribute on the root — this is the dataset the item bands
 * repeat over), parameter declarations ({@code <plain-parameter>} etc.), and
 * expressions ({@code <expression class="...FormulaExpression">} with the
 * formula in a {@code <property name="formula">} child).
 */
final class DataDefinitionParser {

    record Result(
            Optional<String> primaryQueryName,
            List<ParameterDefinition> parameters,
            List<ExpressionDefinition> expressions) {
    }

    Result parse(Document dataDefinitionDoc) {
        Element root = dataDefinitionDoc.getDocumentElement();

        String primaryQuery = XPathSupport.attr(root, "query");
        return new Result(
                Optional.ofNullable(primaryQuery).filter(q -> !q.isBlank()),
                parseParameters(root),
                parseExpressions(root));
    }

    /** Any descendant whose tag ends in "-parameter" (plain-parameter, list-parameter, ...). */
    private List<ParameterDefinition> parseParameters(Element root) {
        List<ParameterDefinition> result = new ArrayList<>();
        for (Element p : descendantsMatching(root, tag -> tag.endsWith("-parameter"))) {
            String name = XPathSupport.attrOrDefault(p, "name", "param");
            String type = simplifyJavaType(XPathSupport.attrOrDefault(p, "type", XPathSupport.localName(p)));
            String defaultValue = XPathSupport.attr(p, "default-value");
            if (defaultValue == null) {
                defaultValue = XPathSupport.findDirectChildren(p, "default-value").stream()
                        .findFirst().map(el -> el.getTextContent().trim()).orElse(null);
            }
            String prompt = firstAttr(p, "prompt", "display-name", "label");
            boolean mandatory = XPathSupport.attrBoolean(p, "mandatory", false);
            result.add(new ParameterDefinition(name, type, defaultValue, prompt, mandatory));
        }
        return result;
    }

    private List<ExpressionDefinition> parseExpressions(Element root) {
        List<ExpressionDefinition> result = new ArrayList<>();
        for (Element e : XPathSupport.findDescendants(root, "expression")) {
            String name = XPathSupport.attrOrDefault(e, "name", "expression");
            String className = XPathSupport.attrOrDefault(e, "class", "");
            String body = XPathSupport.attr(e, "formula");
            if (body == null) {
                body = XPathSupport.findDescendants(e, "property").stream()
                        .filter(prop -> "formula".equalsIgnoreCase(XPathSupport.attrOrDefault(prop, "name", "")))
                        .findFirst()
                        .map(prop -> prop.getTextContent().trim())
                        .orElse("");
            }
            result.add(new ExpressionDefinition(name, languageFromClass(className), body));
        }
        return result;
    }

    private static String languageFromClass(String className) {
        String lower = className.toLowerCase();
        if (lower.contains("formula")) {
            return "formula";
        }
        if (lower.contains("bsh") || lower.contains("beanshell")) {
            return "beanshell";
        }
        if (lower.contains("javascript") || lower.contains("rhino")) {
            return "javascript";
        }
        if (className.isBlank()) {
            return "unknown";
        }
        int lastDot = className.lastIndexOf('.');
        return lastDot >= 0 ? className.substring(lastDot + 1) : className;
    }

    /** "java.util.Date" -> "date", "java.math.BigDecimal" -> "bigdecimal", etc. */
    private static String simplifyJavaType(String type) {
        int lastDot = type.lastIndexOf('.');
        return (lastDot >= 0 ? type.substring(lastDot + 1) : type).toLowerCase();
    }

    private static List<Element> descendantsMatching(Element root, java.util.function.Predicate<String> tagPredicate) {
        return allDescendants(root).stream()
                .filter(el -> tagPredicate.test(XPathSupport.localName(el)))
                .toList();
    }

    private static List<Element> allDescendants(Element root) {
        List<Element> result = new ArrayList<>();
        collect(root, result);
        return result;
    }

    private static void collect(Element el, List<Element> out) {
        for (Element child : XPathSupport.allDirectChildElements(el)) {
            out.add(child);
            collect(child, out);
        }
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
}
