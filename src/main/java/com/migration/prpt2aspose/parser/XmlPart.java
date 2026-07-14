package com.migration.prpt2aspose.parser;

import com.migration.prpt2aspose.util.SecureXmlSupport;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.IOException;

/** One named XML entry extracted from a .prpt archive (e.g. "content.xml"), lazily parseable to a DOM. */
public record XmlPart(String name, byte[] content) {

    public Document parseDocument() throws IOException, SAXException {
        return SecureXmlSupport.parse(new ByteArrayInputStream(content));
    }
}
