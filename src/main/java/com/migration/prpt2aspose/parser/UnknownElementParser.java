package com.migration.prpt2aspose.parser;

import com.migration.prpt2aspose.model.ElementType;
import com.migration.prpt2aspose.model.ParsingWarning;
import com.migration.prpt2aspose.model.ReportElement;
import org.w3c.dom.Element;

import java.util.List;

/** Fallback strategy for any band-child tag this parser doesn't recognize — still captures geometry/style/binding, but flags it. */
final class UnknownElementParser extends AbstractElementParser {
    @Override
    protected ElementType elementType() {
        return ElementType.UNKNOWN;
    }

    @Override
    protected ReportElement enrich(ReportElement element, Element xmlElement, String location, List<ParsingWarning> warnings) {
        warnings.add(ParsingWarning.warning(
                "UNSUPPORTED_ELEMENT", location,
                "Unrecognized report element <" + element.rawTagName() + "> (name='" + element.name()
                        + "'); geometry/style/binding captured best-effort, but this element type needs a manual review before Smart Marker generation."));
        return element;
    }
}
