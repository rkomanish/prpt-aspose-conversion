package com.migration.prpt2aspose.model;

public record ExpressionDefinition(String name, String language, String expressionBody) {

    /** True when this expression uses scripting Aspose has no direct equivalent for (manual-fix territory). */
    public boolean isScripted() {
        return "beanshell".equalsIgnoreCase(language) || "javascript".equalsIgnoreCase(language);
    }
}
