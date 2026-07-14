package com.migration.prpt2aspose.converter;

import com.migration.prpt2aspose.model.GroupDefinition;
import com.migration.prpt2aspose.model.ReportModel;
import com.migration.prpt2aspose.model.WarningSeverity;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** A human-readable digest of a parsed {@link ReportModel}, for CLI dry-run output and later feeding migration-report.html. */
public record ConversionSummary(
        String reportName,
        int parameterCount,
        int dataSourceCount,
        int queryCount,
        int expressionCount,
        int groupCount,
        Map<WarningSeverity, Long> warningCountsBySeverity,
        List<String> warningLines) {

    public static ConversionSummary from(ReportModel model) {
        return new ConversionSummary(
                model.reportName(),
                model.parameters().size(),
                model.dataSources().size(),
                model.totalQueryCount(),
                model.expressions().size(),
                countGroupsRecursively(model.groups()),
                model.warnings().stream()
                        .collect(Collectors.groupingBy(com.migration.prpt2aspose.model.ParsingWarning::severity, Collectors.counting())),
                model.warnings().stream()
                        .map(w -> "[" + w.severity() + "] " + w.category() + " @ " + w.location() + ": " + w.message())
                        .toList());
    }

    private static int countGroupsRecursively(List<GroupDefinition> groups) {
        int count = groups.size();
        for (GroupDefinition group : groups) {
            count += countGroupsRecursively(group.subGroups());
        }
        return count;
    }
}
