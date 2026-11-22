// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.metrics.measurement;

import cz.utulnykoutek.metrics.metrics.definition.MetricId;
import java.util.Map;
import static cz.utulnykoutek.metrics.util.Strings.ensureNonBlank;
import static cz.utulnykoutek.metrics.util.Strings.ensureNonNull;

/** Result of evaluating one metric for one subject in one provider run. */
public record MetricMeasurement(
        MetricId metricId,
        SubjectRef subject,
        String providerRunId,
        MeasurementResult result,
        Map<String, String> attributes) {

    public MetricMeasurement {
        ensureNonNull(metricId, "metricId");
        ensureNonNull(subject, "subject");
        ensureNonNull(result, "result");
        attributes = Map.copyOf(java.util.Objects.requireNonNull(attributes, "attributes"));
        ensureNonBlank(providerRunId, "Provider run ID must not be blank");
    }

    public MetricMeasurement(
            MetricId metricId,
            SubjectRef subject,
            String providerRunId,
            MeasurementResult result) {
        this(metricId, subject, providerRunId, result, Map.of());
    }

    public MeasurementStatus status() {
        return result.status();
    }
}
