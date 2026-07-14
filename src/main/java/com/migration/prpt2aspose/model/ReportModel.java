package com.migration.prpt2aspose.model;

import java.util.ArrayList;
import java.util.List;

/**
 * The fully-parsed, framework-agnostic representation of a PRPT report.
 * This is the contract every later phase (layout, style, Smart Marker
 * generation) builds on top of, and the only thing {@code generator}
 * adapters ever depend on.
 */
public final class ReportModel {

    private final String reportName;
    private final List<ParameterDefinition> parameters;
    private final List<DataSourceDefinition> dataSources;
    private final List<ExpressionDefinition> expressions;
    private final List<GroupDefinition> groups;
    private final ReportBand reportHeaderBand;
    private final ReportBand pageHeaderBand;
    private final ReportBand pageFooterBand;
    private final ReportBand reportFooterBand;
    private final List<ParsingWarning> warnings;

    private ReportModel(Builder builder) {
        this.reportName = builder.reportName;
        this.parameters = List.copyOf(builder.parameters);
        this.dataSources = List.copyOf(builder.dataSources);
        this.expressions = List.copyOf(builder.expressions);
        this.groups = List.copyOf(builder.groups);
        this.reportHeaderBand = builder.reportHeaderBand;
        this.pageHeaderBand = builder.pageHeaderBand;
        this.pageFooterBand = builder.pageFooterBand;
        this.reportFooterBand = builder.reportFooterBand;
        this.warnings = List.copyOf(builder.warnings);
    }

    public String reportName() {
        return reportName;
    }

    public List<ParameterDefinition> parameters() {
        return parameters;
    }

    public List<DataSourceDefinition> dataSources() {
        return dataSources;
    }

    public List<ExpressionDefinition> expressions() {
        return expressions;
    }

    public List<GroupDefinition> groups() {
        return groups;
    }

    public ReportBand reportHeaderBand() {
        return reportHeaderBand;
    }

    public ReportBand pageHeaderBand() {
        return pageHeaderBand;
    }

    public ReportBand pageFooterBand() {
        return pageFooterBand;
    }

    public ReportBand reportFooterBand() {
        return reportFooterBand;
    }

    public List<ParsingWarning> warnings() {
        return warnings;
    }

    public int totalQueryCount() {
        return dataSources.stream().mapToInt(ds -> ds.queries().size()).sum();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String reportName = "Unnamed Report";
        private final List<ParameterDefinition> parameters = new ArrayList<>();
        private final List<DataSourceDefinition> dataSources = new ArrayList<>();
        private final List<ExpressionDefinition> expressions = new ArrayList<>();
        private final List<GroupDefinition> groups = new ArrayList<>();
        private ReportBand reportHeaderBand = ReportBand.empty(BandType.REPORT_HEADER);
        private ReportBand pageHeaderBand = ReportBand.empty(BandType.PAGE_HEADER);
        private ReportBand pageFooterBand = ReportBand.empty(BandType.PAGE_FOOTER);
        private ReportBand reportFooterBand = ReportBand.empty(BandType.REPORT_FOOTER);
        private final List<ParsingWarning> warnings = new ArrayList<>();

        private Builder() {
        }

        public Builder reportName(String reportName) {
            this.reportName = reportName;
            return this;
        }

        public Builder addParameter(ParameterDefinition parameter) {
            this.parameters.add(parameter);
            return this;
        }

        public Builder addDataSource(DataSourceDefinition dataSource) {
            this.dataSources.add(dataSource);
            return this;
        }

        public Builder addExpression(ExpressionDefinition expression) {
            this.expressions.add(expression);
            return this;
        }

        public Builder addGroup(GroupDefinition group) {
            this.groups.add(group);
            return this;
        }

        public Builder reportHeaderBand(ReportBand band) {
            this.reportHeaderBand = band;
            return this;
        }

        public Builder pageHeaderBand(ReportBand band) {
            this.pageHeaderBand = band;
            return this;
        }

        public Builder pageFooterBand(ReportBand band) {
            this.pageFooterBand = band;
            return this;
        }

        public Builder reportFooterBand(ReportBand band) {
            this.reportFooterBand = band;
            return this;
        }

        public Builder addWarning(ParsingWarning warning) {
            this.warnings.add(warning);
            return this;
        }

        public Builder addWarnings(List<ParsingWarning> more) {
            this.warnings.addAll(more);
            return this;
        }

        public ReportModel build() {
            return new ReportModel(this);
        }
    }
}
