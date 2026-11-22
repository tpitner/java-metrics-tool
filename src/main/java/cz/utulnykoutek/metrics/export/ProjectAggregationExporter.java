// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.export;

import cz.utulnykoutek.metrics.metrics.aggregation.AggregatedStatistics;
import cz.utulnykoutek.metrics.metrics.aggregation.AggregationCounts;
import cz.utulnykoutek.metrics.metrics.aggregation.DescriptiveStatistics;
import cz.utulnykoutek.metrics.metrics.aggregation.EndpointAggregation;
import cz.utulnykoutek.metrics.metrics.aggregation.EndpointStatistics;
import cz.utulnykoutek.metrics.metrics.aggregation.MetricAggregation;
import cz.utulnykoutek.metrics.metrics.aggregation.NoAggregatableValues;
import cz.utulnykoutek.metrics.metrics.aggregation.NoEndpointValues;
import cz.utulnykoutek.metrics.metrics.aggregation.SensitivityAggregation;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

public final class ProjectAggregationExporter {
    private ProjectAggregationExporter() {}

    public static String toJson(List<MetricAggregation> aggregations) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n  \"aggregations\": [");
        for (int i = 0; i < aggregations.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\n    ").append(aggregationJson(aggregations.get(i)));
        }
        if (!aggregations.isEmpty()) sb.append("\n  ");
        sb.append("]\n}\n");
        return sb.toString();
    }

    private static String aggregationJson(MetricAggregation aggregation) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"metricId\":").append(q(aggregation.metricId().serializedName()));
        sb.append(",\"sourceScope\":").append(q(aggregation.sourceScope().serializedName()));
        sb.append(",\"providerRunId\":").append(q(aggregation.providerRunId()));
        sb.append(",\"counts\":").append(countsJson(aggregation.counts()));
        sb.append(",\"result\":").append(resultJson(aggregation.result()));
        sb.append(",\"sensitivity\":").append(sensitivityJson(aggregation.sensitivity()));
        sb.append("}");
        return sb.toString();
    }

    private static String countsJson(AggregationCounts counts) {
        return "{\"nTotal\":" + counts.nTotal()
                + ",\"nOk\":" + counts.nOk()
                + ",\"nPartial\":" + counts.nPartial()
                + ",\"nNotApplicable\":" + counts.nNotApplicable()
                + ",\"nUnavailable\":" + counts.nUnavailable() + "}";
    }

    private static String resultJson(cz.utulnykoutek.metrics.metrics.aggregation.AggregationResult result) {
        if (result instanceof NoAggregatableValues) {
            return "{\"kind\":\"none\"}";
        }
        if (result instanceof AggregatedStatistics statistics) {
            return "{\"kind\":\"statistics\"," + statisticsJson(statistics.statistics()) + "}";
        }
        return "{\"kind\":\"unknown\"}";
    }

    private static String statisticsJson(DescriptiveStatistics s) {
        return "\"count\":" + s.count()
                + ",\"sum\":" + s.sum().toPlainString()
                + ",\"mean\":" + s.mean().toPlainString()
                + ",\"median\":" + s.median().toPlainString()
                + ",\"minimum\":" + s.minimum().toPlainString()
                + ",\"maximum\":" + s.maximum().toPlainString()
                + ",\"p25\":" + s.p25().toPlainString()
                + ",\"p75\":" + s.p75().toPlainString()
                + ",\"iqr\":" + s.iqr().toPlainString()
                + ",\"p90\":" + s.p90().toPlainString()
                + ",\"p95\":" + s.p95().toPlainString()
                + ",\"p99\":" + s.p99().toPlainString();
    }

    private static String sensitivityJson(SensitivityAggregation sensitivity) {
        return "{\"lowerEndpoints\":" + endpointJson(sensitivity.lowerEndpoints())
                + ",\"upperEndpoints\":" + endpointJson(sensitivity.upperEndpoints())
                + ",\"nObservedFragments\":" + sensitivity.nObservedFragments()
                + ",\"reasons\":" + enumNames(sensitivity.reasons())
                + ",\"coverage\":" + coverageJson(sensitivity.coverage()) + "}";
    }

    private static String endpointJson(EndpointAggregation endpoint) {
        if (endpoint instanceof NoEndpointValues) {
            return "{\"kind\":\"none\"}";
        }
        if (endpoint instanceof EndpointStatistics statistics) {
            return "{\"kind\":\"statistics\"," + statisticsJson(statistics.statistics()) + "}";
        }
        return "{\"kind\":\"unknown\"}";
    }

    private static String coverageJson(List<cz.utulnykoutek.metrics.metrics.measurement.CoverageEvidence> coverage) {
        return "[" + coverage.stream()
                .map(item -> "{\"dimension\":" + q(item.dimension().name())
                        + ",\"resolved\":" + item.resolved()
                        + ",\"total\":" + item.total() + "}")
                .collect(Collectors.joining(",")) + "]";
    }

    private static String enumNames(java.util.Set<? extends Enum<?>> values) {
        return "[" + values.stream().map(v -> q(v.name())).collect(Collectors.joining(",")) + "]";
    }

    private static String q(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    public static String toCsv(List<MetricAggregation> aggregations) {
        StringBuilder sb = new StringBuilder();
        sb.append("metricId,sourceScope,providerRunId,variant,statistic,value\n");
        for (MetricAggregation aggregation : aggregations) {
            String metricId = aggregation.metricId().serializedName();
            String scope = aggregation.sourceScope().serializedName();
            String run = aggregation.providerRunId();
            if (aggregation.result() instanceof AggregatedStatistics statistics) {
                appendStats(sb, metricId, scope, run, "primary", statistics.statistics());
            }
            SensitivityAggregation sensitivity = aggregation.sensitivity();
            if (sensitivity.lowerEndpoints() instanceof EndpointStatistics lower) {
                appendStats(sb, metricId, scope, run, "sensitivity-lower", lower.statistics());
            }
            if (sensitivity.upperEndpoints() instanceof EndpointStatistics upper) {
                appendStats(sb, metricId, scope, run, "sensitivity-upper", upper.statistics());
            }
        }
        return sb.toString();
    }

    private static void appendStats(StringBuilder sb, String metricId, String scope, String run,
                                   String variant, DescriptiveStatistics s) {
        appendRow(sb, metricId, scope, run, variant, "count", String.valueOf(s.count()));
        appendRow(sb, metricId, scope, run, variant, "sum", s.sum().toPlainString());
        appendRow(sb, metricId, scope, run, variant, "mean", s.mean().toPlainString());
        appendRow(sb, metricId, scope, run, variant, "median", s.median().toPlainString());
        appendRow(sb, metricId, scope, run, variant, "minimum", s.minimum().toPlainString());
        appendRow(sb, metricId, scope, run, variant, "maximum", s.maximum().toPlainString());
        appendRow(sb, metricId, scope, run, variant, "p25", s.p25().toPlainString());
        appendRow(sb, metricId, scope, run, variant, "p75", s.p75().toPlainString());
        appendRow(sb, metricId, scope, run, variant, "iqr", s.iqr().toPlainString());
        appendRow(sb, metricId, scope, run, variant, "p90", s.p90().toPlainString());
        appendRow(sb, metricId, scope, run, variant, "p95", s.p95().toPlainString());
        appendRow(sb, metricId, scope, run, variant, "p99", s.p99().toPlainString());
    }

    private static void appendRow(StringBuilder sb, String metricId, String scope, String run,
                                 String variant, String statistic, String value) {
        sb.append(metricId).append(',').append(scope).append(',').append(run).append(',')
                .append(variant).append(',').append(statistic).append(',').append(value).append('\n');
    }
}