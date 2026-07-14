package com.migration.prpt2aspose.parser;

import com.migration.prpt2aspose.model.ElementType;

final class TextFieldElementParser extends AbstractElementParser {
    @Override
    protected ElementType elementType() {
        return ElementType.TEXT_FIELD;
    }
}
