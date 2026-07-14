package com.migration.prpt2aspose.parser;

import java.util.Map;

/** Maps a band-child XML tag's local name to the {@link ElementParser} strategy that understands it. */
final class ElementParserFactory {

    private static final Map<String, ElementParser> PARSERS = Map.of(
            "label", new LabelElementParser(),
            "text-field", new TextFieldElementParser(),
            "number-field", new NumberFieldElementParser(),
            "date-field", new DateFieldElementParser(),
            "image-field", new ImageFieldElementParser(),
            "line", new LineElementParser(),
            "rectangle", new RectangleElementParser());

    private static final ElementParser FALLBACK = new UnknownElementParser();

    private ElementParserFactory() {
    }

    static ElementParser forTag(String localName) {
        return PARSERS.getOrDefault(localName, FALLBACK);
    }
}
