// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.metrics;

import cz.utulnykoutek.metrics.metrics.definition.BuiltInMetricDefinition;
import cz.utulnykoutek.metrics.metrics.measurement.MetricMeasurement;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Computes one coherent group of metrics implemented by java-metrics itself. */
public interface BuiltInMetricComputer {
    Set<BuiltInMetricDefinition> definitions();

    List<MetricMeasurement> compute(AnalysisContext context, String providerRunId);

    /** Computes only requested definitions; implementations may override to avoid unnecessary work. */
    default List<MetricMeasurement> compute(
            AnalysisContext context,
            String providerRunId,
            Set<BuiltInMetricDefinition> requestedDefinitions) {
        Objects.requireNonNull(requestedDefinitions, "requestedDefinitions");
        if (!definitions().containsAll(requestedDefinitions)) {
            throw new IllegalArgumentException("Computer does not implement all requested definitions");
        }
        return compute(context, providerRunId).stream()
                .filter(measurement -> requestedDefinitions.stream()
                        .anyMatch(definition -> definition.id().equals(measurement.metricId())))
                .toList();
    }
}
