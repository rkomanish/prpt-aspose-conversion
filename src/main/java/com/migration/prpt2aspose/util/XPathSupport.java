package com.migration.prpt2aspose.util;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.util.ArrayList;
import java.util.List;

/**
 * Namespace-agnostic XML querying: every lookup matches on local element name
 * only via {@code local-name()}, deliberately ignoring whatever namespace URI
 * a given PRPT/Report-Designer version happens to declare. This is the core
 * mechanism behind the "tolerant" parsing strategy — see the architecture
 * plan's rationale for DOM+XPath over JAXB.
 */
public final class XPathSupport {

    private XPathSupport() {
    }

    /** All descendants (at any depth) of {@code context} with the given local name. */
    public static List<Element> findDescendants(Node context, String localName) {
        return evaluate(context, ".//*[local-name()='" + localName + "']");
    }

    /** Direct children of {@code context} with the given local name. */
    public static List<Element> findDirectChildren(Node context, String localName) {
        return evaluate(context, "./*[local-name()='" + localName + "']");
    }

    /** All direct child elements of {@code context}, regardless of tag name, in document order. */
    public static List<Element> allDirectChildElements(Node context) {
        List<Element> result = new ArrayList<>();
        NodeList children = context.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                result.add((Element) child);
            }
        }
        return result;
    }

    public static String attr(Element element, String name) {
        if (!element.hasAttribute(name)) {
            return null;
        }
        return element.getAttribute(name);
    }

    public static String attrOrDefault(Element element, String name, String defaultValue) {
        String value = attr(element, name);
        return value != null ? value : defaultValue;
    }

    public static double attrDouble(Element element, String name, double defaultValue) {
        String value = attr(element, name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static boolean attrBoolean(Element element, String name, boolean defaultValue) {
        String value = attr(element, name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value.trim());
    }

    /** Local name of an element, ignoring namespace prefix. */
    public static String localName(Element element) {
        String local = element.getLocalName();
        return local != null ? local : element.getTagName();
    }

    private static List<Element> evaluate(Node context, String expression) {
        XPath xpath = XPathFactory.newInstance().newXPath();
        try {
            NodeList nodes = (NodeList) xpath.evaluate(expression, context, XPathConstants.NODESET);
            List<Element> result = new ArrayList<>(nodes.getLength());
            for (int i = 0; i < nodes.getLength(); i++) {
                result.add((Element) nodes.item(i));
            }
            return result;
        } catch (XPathExpressionException e) {
            throw new IllegalStateException("Invalid XPath expression: " + expression, e);
        }
    }
}
