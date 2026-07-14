package com.migration.prpt2aspose.model;

/**
 * How an element's runtime value is sourced. Exactly one of
 * {@code fieldName}/{@code formula}/{@code expressionRef} is populated,
 * matching {@code type}; the others are {@code null}.
 */
public record FieldBinding(FieldBindingType type, String fieldName, String formula, String expressionRef) {

    public static final FieldBinding NONE = new FieldBinding(FieldBindingType.NONE, null, null, null);

    public static FieldBinding directField(String fieldName) {
        return new FieldBinding(FieldBindingType.DIRECT_FIELD, fieldName, null, null);
    }

    public static FieldBinding formula(String formula) {
        return new FieldBinding(FieldBindingType.FORMULA, null, formula, null);
    }

    public static FieldBinding expressionRef(String expressionName) {
        return new FieldBinding(FieldBindingType.EXPRESSION_REF, null, null, expressionName);
    }
}
