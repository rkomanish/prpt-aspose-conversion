package com.migration.prpt2aspose.model;

public record ParameterDefinition(
        String name,
        String type,
        String defaultValue,
        String prompt,
        boolean mandatory) {
}
