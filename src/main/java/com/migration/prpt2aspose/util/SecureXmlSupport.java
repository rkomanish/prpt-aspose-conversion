package com.migration.prpt2aspose.util;

import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;

/**
 * Hardened DOM parsing shared by every PRPT sub-parser. External entities and
 * DTD processing are disabled to prevent XXE; namespace-awareness is enabled
 * but callers should still prefer {@link XPathSupport}'s local-name-based
 * queries over fixed namespace URIs, since Pentaho's report namespace URI has
 * drifted across Report Designer versions.
 */
public final class SecureXmlSupport {

    private SecureXmlSupport() {
    }

    public static Document parse(InputStream input) throws IOException, SAXException {
        try {
            return newDocumentBuilder().parse(input);
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException("Unable to configure a secure XML parser", e);
        }
    }

    public static DocumentBuilder newDocumentBuilder() throws ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);

        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

        return factory.newDocumentBuilder();
    }
}
