package com.migration.prpt2aspose.parser;

import com.migration.prpt2aspose.model.ElementType;

final class RectangleElementParser extends AbstractElementParser {
    @Override
    protected ElementType elementType() {
        return ElementType.RECTANGLE;
    }
}
