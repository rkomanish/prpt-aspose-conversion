package com.migration.prpt2aspose.parser;

import com.migration.prpt2aspose.model.ElementType;
import com.migration.prpt2aspose.model.FieldBindingType;
import com.migration.prpt2aspose.model.ParsingWarning;
import com.migration.prpt2aspose.model.ReportElement;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ElementParserFactoryTest {

    private Document newDocument() throws ParserConfigurationException {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
    }

    @Test
    void resolvesKnownTagsToTypedElements() throws ParserConfigurationException {
        Document doc = newDocument();
        Element textField = doc.createElement("text-field");
        textField.setAttribute("name", "customerName");
        textField.setAttribute("field", "CustomerName");
        textField.setAttribute("x", "10");
        textField.setAttribute("y", "20");
        textField.setAttribute("width", "100");
        textField.setAttribute("height", "15");
        textField.setAttribute("bold", "true");

        List<ParsingWarning> warnings = new ArrayList<>();
        ReportElement element = ElementParserFactory.forTag("text-field").parse(textField, "test-location", warnings);

        assertThat(element.type()).isEqualTo(ElementType.TEXT_FIELD);
        assertThat(element.name()).isEqualTo("customerName");
        assertThat(element.binding().type()).isEqualTo(FieldBindingType.DIRECT_FIELD);
        assertThat(element.binding().fieldName()).isEqualTo("CustomerName");
        assertThat(element.geometry().x()).isEqualTo(10);
        assertThat(element.geometry().width()).isEqualTo(100);
        assertThat(element.style().bold()).isTrue();
        assertThat(warnings).isEmpty();
    }

    @Test
    void formulaBindingIsDetectedFromLeadingEquals() throws ParserConfigurationException {
        Document doc = newDocument();
        Element numberField = doc.createElement("number-field");
        numberField.setAttribute("field", "=SUM([Amount])");

        ReportElement element = ElementParserFactory.forTag("number-field").parse(numberField, "loc", new ArrayList<>());

        assertThat(element.binding().type()).isEqualTo(FieldBindingType.FORMULA);
        assertThat(element.binding().formula()).isEqualTo("=SUM([Amount])");
    }

    @Test
    void unrecognizedTagFallsBackAndRecordsWarningInsteadOfThrowing() throws ParserConfigurationException {
        Document doc = newDocument();
        Element mystery = doc.createElement("weird-custom-element");
        mystery.setAttribute("name", "mystery");

        List<ParsingWarning> warnings = new ArrayList<>();
        ReportElement element = ElementParserFactory.forTag("weird-custom-element").parse(mystery, "loc", warnings);

        assertThat(element.type()).isEqualTo(ElementType.UNKNOWN);
        assertThat(element.rawTagName()).isEqualTo("weird-custom-element");
        assertThat(warnings).hasSize(1);
        assertThat(warnings.get(0).category()).isEqualTo("UNSUPPORTED_ELEMENT");
    }

    @Test
    void imageFieldWithoutSourceRecordsWarning() throws ParserConfigurationException {
        Document doc = newDocument();
        Element image = doc.createElement("image-field");
        image.setAttribute("name", "logo");

        List<ParsingWarning> warnings = new ArrayList<>();
        ElementParserFactory.forTag("image-field").parse(image, "loc", warnings);

        assertThat(warnings).anySatisfy(w -> assertThat(w.category()).isEqualTo("IMAGE_SOURCE_UNRESOLVED"));
    }
}
