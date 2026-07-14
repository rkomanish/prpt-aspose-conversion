package com.migration.prpt2aspose.model;

/** A named SQL query, e.g. from {@code <sql-datasource><query name="Orders">...}. This is the source of truth for queries.sql. */
public record QueryDefinition(String name, String sql) {
}
