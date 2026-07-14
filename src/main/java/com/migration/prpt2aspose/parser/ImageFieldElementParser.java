package com.migration.prpt2aspose.parser;

import com.migration.prpt2aspose.model.ElementType;
import com.migration.prpt2aspose.model.ParsingWarning;
import com.migration.prpt2aspose.model.ReportElement;
import com.migration.prpt2aspose.util.XPathSupport;
import org.w3c.dom.Element;

import java.util.List;

final class ImageFieldElementParser extends AbstractElementParser {
    @Override
    protected ElementType elementType() {
        return ElementType.IMAGE_FIELD;
    }

    @Override
    protected ReportElement enrich(ReportElement element, Element xmlElement, String location, List<ParsingWarning> warnings) {
        boolean hasSource = XPathSupport.attr(xmlElement, "src") != null
                || XPathSupport.attr(xmlElement, "href") != null
                || XPathSupport.attr(xmlElement, "image") != null
                || element.binding().type() != com.migration.prpt2aspose.model.FieldBindingType.NONE;
        if (!hasSource) {
            warnings.add(ParsingWarning.warning(
                    "IMAGE_SOURCE_UNRESOLVED", location,
                    "Image element '" + element.name() + "' has no recognizable source attribute (src/href/image) or field binding; embedded resource lookup will need a manual check."));
        }
        return element;
    }
}
