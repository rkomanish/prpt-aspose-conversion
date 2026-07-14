package com.migration.prpt2aspose.model;

import java.util.List;

public record DataSourceDefinition(String type, List<QueryDefinition> queries) {

    public DataSourceDefinition {
        queries = List.copyOf(queries);
    }
}
