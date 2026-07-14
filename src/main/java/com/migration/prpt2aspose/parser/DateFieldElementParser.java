package com.migration.prpt2aspose.parser;

import com.migration.prpt2aspose.model.ElementType;

/** Date formatting (e.g. the "format" attribute) is preserved via StyleDefinition.rawAttributes for now; typed mapping is Phase 6. */
final class DateFieldElementParser extends AbstractElementParser {
    @Override
    protected ElementType elementType() {
        return ElementType.DATE_FIELD;
    }
}
