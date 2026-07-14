package com.migration.prpt2aspose.parser;

import com.migration.prpt2aspose.model.ParsingWarning;
import com.migration.prpt2aspose.model.ReportElement;
import org.w3c.dom.Element;

import java.util.List;

/**
 * Strategy for turning one band-child XML element into a {@link ReportElement}.
 * Selected at runtime by {@link ElementParserFactory} based on the XML tag's
 * local name.
 */
public interface ElementParser {

    ReportElement parse(Element xmlElement, String location, List<ParsingWarning> warnings);
}
