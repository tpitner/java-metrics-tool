// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.metrics.measurement;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import cz.utulnykoutek.metrics.metrics.definition.MetricCatalog;
import cz.utulnykoutek.metrics.metrics.definition.MetricDefinition;
import cz.utulnykoutek.metrics.metrics.definition.MetricId;
import static cz.utulnykoutek.metrics.util.Lists.filtered;
import static cz.utulnykoutek.metrics.util.Strings.ensureNonBlank;
import static cz.utulnykoutek.metrics.util.Strings.ensureNonNull;
import static cz.utulnykoutek.metrics.util.Strings.requireNonNull;

/** Immutable, validated collection of measurements from one analysis. */
public final class MeasurementStore {
    private final Map<MeasurementKey, MetricMeasurement> byKey;
    private final List<MetricMeasurement> measurements;

    private MeasurementStore(Map<MeasurementKey, MetricMeasurement> measurements) {
        this.byKey = Map.copyOf(measurements);
        this.measurements = measurements.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .toList();
    }

    public static Builder builder(MetricCatalog catalog) {
        return new Builder(catalog);
    }

    public Optional<MetricMeasurement> find(MeasurementKey key) {
        return Optional.ofNullable(byKey.get(requireNonNull(key, "key")));
    }

    public List<MetricMeasurement> forSubject(SubjectRef subject) {
        ensureNonNull(subject, "subject");
        return filtered(measurements, measurement -> measurement.subject().equals(subject));
    }

    public List<MetricMeasurement> forMetric(MetricId metricId) {
        ensureNonNull(metricId, "metricId");
        return filtered(measurements, measurement -> measurement.metricId().equals(metricId));
    }

    public List<MetricMeasurement> fromProviderRun(String providerRunId) {
        ensureNonBlank(providerRunId, "Provider run ID must not be blank");
        return filtered(measurements, measurement -> measurement.providerRunId().equals(providerRunId));
    }

    /** Returns all measurements in subject, metric, provider-run order. */
    public List<MetricMeasurement> all() {
        return measurements;
    }

    public int size() {
        return measurements.size();
    }

    public boolean isEmpty() {
        return measurements.isEmpty();
    }

    public record Builder(MetricCatalog catalog, Map<MeasurementKey, MetricMeasurement> measurements) {
        public Builder(MetricCatalog catalog) {
            this(catalog, new HashMap<>());
        }

        public Builder {
            ensureNonNull(catalog, "catalog");
            ensureNonNull(measurements, "measurements");
        }

        public Builder add(MetricMeasurement measurement) {
            ensureNonNull(measurement, "measurement");
            validate(measurement);

            MeasurementKey key = MeasurementKey.of(measurement);
            MetricMeasurement previous = measurements.putIfAbsent(key, measurement);
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate measurement: " + key);
            }
            return this;
        }

        public Builder addAll(Iterable<? extends MetricMeasurement> measurements) {
            ensureNonNull(measurements, "measurements");
            measurements.forEach(this::add);
            return this;
        }

        public MeasurementStore build() {
            return new MeasurementStore(measurements);
        }

        private void validate(MetricMeasurement measurement) {
            MetricDefinition definition = catalog.require(measurement.metricId());
            if (!definition.supports(measurement.subject().scope())) {
                throw new IllegalArgumentException(
                        "Metric " + definition.id() + " does not support scope "
                                + measurement.subject().scope());
            }

            switch (measurement.result()) {
                case NumericResult numeric -> definition.validate(numeric.value());
                case IntervalResult interval -> {
                    definition.validate(interval.lower());
                    definition.validate(interval.upper());
                }
                case UnavailableResult ignored -> { }
                case NotApplicableResult ignored -> { }
            }
        }
    }
}
