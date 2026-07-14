package com.migration.prpt2aspose.model;

/**
 * How a report element's value is sourced. DIRECT_FIELD covers plain
 * {@code field="ColumnName"} bindings; FORMULA covers OpenFormula-style
 * {@code formula="=[ColumnName]"} expressions; EXPRESSION_REF covers elements
 * that reference a named {@code <expressions>} entry instead of inlining one.
 */
public enum FieldBindingType {
    DIRECT_FIELD,
    FORMULA,
    EXPRESSION_REF,
    NONE
}
