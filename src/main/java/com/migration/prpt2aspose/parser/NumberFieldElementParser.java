package com.migration.prpt2aspose.parser;

import com.migration.prpt2aspose.model.ElementType;

/** Number formatting (e.g. the "format" attribute) is preserved via StyleDefinition.rawAttributes for now; typed mapping is Phase 6. */
final class NumberFieldElementParser extends AbstractElementParser {
    @Override
    protected ElementType elementType() {
        return ElementType.NUMBER_FIELD;
    }
}
