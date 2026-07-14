package com.migration.prpt2aspose.util;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class XPathSupportTest {

    @Test
    void findsElementsByLocalNameIgnoringNamespace() throws Exception {
        String xml = """
                <?xml version="1.0"?>
                <root xmlns:p="http://some.namespace/that/varies/by/version">
                    <p:band><p:label name="a"/></p:band>
                    <p:band><p:label name="b"/></p:band>
                </root>
                """;
        Document doc = SecureXmlSupport.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

        List<Element> labels = XPathSupport.findDescendants(doc, "label");

        assertThat(labels).hasSize(2);
        assertThat(labels).extracting(el -> XPathSupport.attr(el, "name")).containsExactly("a", "b");
    }

    @Test
    void directChildrenDoNotIncludeDeeperDescendants() throws Exception {
        String xml = "<root><a/><b><a/></b></root>";
        Document doc = SecureXmlSupport.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

        List<Element> directA = XPathSupport.findDirectChildren(doc.getDocumentElement(), "a");

        assertThat(directA).hasSize(1);
    }

    @Test
    void externalEntitiesAreDisabled() {
        String malicious = """
                <?xml version="1.0"?>
                <!DOCTYPE root [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <root>&xxe;</root>
                """;

        org.junit.jupiter.api.Assertions.assertThrows(Exception.class,
                () -> SecureXmlSupport.parse(new ByteArrayInputStream(malicious.getBytes(StandardCharsets.UTF_8))));
    }
}
