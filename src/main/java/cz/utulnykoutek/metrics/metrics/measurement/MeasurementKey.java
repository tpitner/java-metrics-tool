// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.metrics.measurement;

import static cz.utulnykoutek.metrics.util.Strings.ensureNonBlank;
import static cz.utulnykoutek.metrics.util.Strings.ensureNonNull;

import cz.utulnykoutek.metrics.metrics.definition.MetricId;

/** Unique identity of a measurement within an analysis report. */
public record MeasurementKey(
        MetricId metricId,
        SubjectRef subject,
        String providerRunId) implements Comparable<MeasurementKey> {

    public MeasurementKey {
        ensureNonNull(metricId, "metricId");
        ensureNonNull(subject, "subject");
        ensureNonBlank(providerRunId, "Provider run ID must not be blank");
    }

    public static MeasurementKey of(MetricMeasurement measurement) {
        ensureNonNull(measurement, "measurement");
        return new MeasurementKey(
                measurement.metricId(),
                measurement.subject(),
                measurement.providerRunId());
    }

    @Override
    public int compareTo(MeasurementKey other) {
        ensureNonNull(other, "other");

        int subjectComparison = subject.compareTo(other.subject);
        if (subjectComparison != 0) {
            return subjectComparison;
        }

        int metricComparison = metricId.compareTo(other.metricId);
        if (metricComparison != 0) {
            return metricComparison;
        }

        return providerRunId.compareTo(other.providerRunId);
    }
}
