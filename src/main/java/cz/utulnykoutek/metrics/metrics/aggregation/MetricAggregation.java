// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.metrics.aggregation;

import cz.utulnykoutek.metrics.metrics.definition.MetricId;
import cz.utulnykoutek.metrics.metrics.definition.MetricScope;

import java.util.Objects;

import static cz.utulnykoutek.metrics.util.Strings.ensureNonBlank;

/** Primary project-level aggregation for one metric, source scope and provider run. */
public record MetricAggregation(
        MetricId metricId,
        MetricScope sourceScope,
        String providerRunId,
        AggregationCounts counts,
        AggregationResult result,
        SensitivityAggregation sensitivity) {

    public MetricAggregation {
        Objects.requireNonNull(metricId, "metricId");
        Objects.requireNonNull(sourceScope, "sourceScope");
        ensureNonBlank(providerRunId, "Provider run ID must not be blank");
        Objects.requireNonNull(counts, "counts");
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(sensitivity, "sensitivity");
        if (result instanceof AggregatedStatistics statistics
                && statistics.statistics().count() != counts.nOk()) {
            throw new IllegalArgumentException("Statistics count must equal the OK count");
        }
        if (result instanceof NoAggregatableValues && counts.nOk() != 0) {
            throw new IllegalArgumentException("No-value result requires zero OK measurements");
        }
    }
}
