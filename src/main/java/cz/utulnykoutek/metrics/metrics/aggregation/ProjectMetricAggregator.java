// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.metrics.aggregation;

import cz.utulnykoutek.metrics.metrics.definition.MetricId;
import cz.utulnykoutek.metrics.metrics.definition.MetricScope;
import cz.utulnykoutek.metrics.metrics.measurement.MeasurementStatus;
import cz.utulnykoutek.metrics.metrics.measurement.MeasurementStore;
import cz.utulnykoutek.metrics.metrics.measurement.MetricMeasurement;
import cz.utulnykoutek.metrics.metrics.measurement.NotApplicableResult;
import cz.utulnykoutek.metrics.metrics.measurement.IntervalResult;
import cz.utulnykoutek.metrics.metrics.measurement.NumericResult;
import cz.utulnykoutek.metrics.metrics.measurement.UnavailableResult;
import cz.utulnykoutek.metrics.metrics.measurement.ReasonCode;
import cz.utulnykoutek.metrics.metrics.measurement.ValueRelation;
import cz.utulnykoutek.metrics.metrics.measurement.CoverageEvidence;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

import static cz.utulnykoutek.metrics.util.Strings.ensureNonBlank;

/** Computes the D12 primary project aggregation from exact {@code OK} values only. */
public final class ProjectMetricAggregator {
    private static final MathContext MATH = MathContext.DECIMAL128;

    private ProjectMetricAggregator() {}

    public static MetricAggregation aggregate(
            MeasurementStore store,
            MetricId metricId,
            MetricScope sourceScope,
            String providerRunId) {
        Objects.requireNonNull(store, "store");
        Objects.requireNonNull(metricId, "metricId");
        Objects.requireNonNull(sourceScope, "sourceScope");
        ensureNonBlank(providerRunId, "Provider run ID must not be blank");

        List<MetricMeasurement> selected = store.forMetric(metricId).stream()
                .filter(measurement -> measurement.subject().scope() == sourceScope)
                .filter(measurement -> measurement.providerRunId().equals(providerRunId))
                .toList();
        int ok = count(selected, MeasurementStatus.OK);
        AggregationCounts counts = new AggregationCounts(selected.size(), ok,
                count(selected, MeasurementStatus.PARTIAL),
                count(selected, MeasurementStatus.NOT_APPLICABLE),
                count(selected, MeasurementStatus.UNAVAILABLE));
        List<BigDecimal> values = new ArrayList<>(ok);
        for (MetricMeasurement measurement : selected) {
            if (measurement.status() == MeasurementStatus.OK) {
                values.add(((NumericResult) measurement.result()).value());
            }
        }
        values.sort(Comparator.naturalOrder());
        AggregationResult primary = values.isEmpty()
                ? new NoAggregatableValues()
                : new AggregatedStatistics(statistics(values));
        return new MetricAggregation(metricId, sourceScope, providerRunId, counts,
                primary, sensitivity(selected));
    }

    private static SensitivityAggregation sensitivity(List<MetricMeasurement> measurements) {
        List<BigDecimal> lower = new ArrayList<>();
        List<BigDecimal> upper = new ArrayList<>();
        int observedFragments = 0;
        EnumSet<ReasonCode> reasons = EnumSet.noneOf(ReasonCode.class);
        LinkedHashSet<CoverageEvidence> coverage = new LinkedHashSet<>();
        for (MetricMeasurement measurement : measurements) {
            switch (measurement.result()) {
                case NumericResult numeric -> {
                    switch (numeric.relation()) {
                        case EXACT -> {
                            lower.add(numeric.value());
                            upper.add(numeric.value());
                        }
                        case AT_LEAST -> lower.add(numeric.value());
                        case AT_MOST -> upper.add(numeric.value());
                        case OBSERVED_FRAGMENT -> observedFragments++;
                    }
                    reasons.addAll(numeric.reasons());
                    coverage.addAll(numeric.coverage());
                }
                case IntervalResult interval -> {
                    lower.add(interval.lower());
                    upper.add(interval.upper());
                    reasons.addAll(interval.reasons());
                    coverage.addAll(interval.coverage());
                }
                case NotApplicableResult notApplicable -> reasons.add(notApplicable.reason());
                case UnavailableResult unavailable -> reasons.addAll(unavailable.reasons());
                default -> { }
            }
        }
        lower.sort(Comparator.naturalOrder());
        upper.sort(Comparator.naturalOrder());
        List<CoverageEvidence> sortedCoverage = coverage.stream()
                .sorted(Comparator.comparing((CoverageEvidence item) -> item.dimension().name())
                        .thenComparingLong(CoverageEvidence::resolved)
                        .thenComparingLong(CoverageEvidence::total))
                .toList();
        return new SensitivityAggregation(endpoint(lower), endpoint(upper),
                observedFragments, reasons, sortedCoverage);
    }

    private static EndpointAggregation endpoint(List<BigDecimal> values) {
        return values.isEmpty()
                ? new NoEndpointValues()
                : new EndpointStatistics(statistics(values));
    }

    private static int count(List<MetricMeasurement> measurements, MeasurementStatus status) {
        return Math.toIntExact(measurements.stream()
                .filter(measurement -> measurement.status() == status).count());
    }

    private static DescriptiveStatistics statistics(List<BigDecimal> sorted) {
        BigDecimal sum = sorted.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal p25 = quantile(sorted, new BigDecimal("0.25"));
        BigDecimal p75 = quantile(sorted, new BigDecimal("0.75"));
        return new DescriptiveStatistics(sorted.size(), sum,
                sum.divide(BigDecimal.valueOf(sorted.size()), MATH),
                quantile(sorted, new BigDecimal("0.5")),
                sorted.getFirst(), sorted.getLast(), p25, p75, p75.subtract(p25),
                quantile(sorted, new BigDecimal("0.9")),
                quantile(sorted, new BigDecimal("0.95")),
                quantile(sorted, new BigDecimal("0.99")));
    }

    /** Hyndman--Fan Type 7 quantile for a sorted non-empty sample. */
    static BigDecimal quantile(List<BigDecimal> sorted, BigDecimal probability) {
        if (sorted.isEmpty()) throw new IllegalArgumentException("Quantile requires values");
        if (probability.compareTo(BigDecimal.ZERO) < 0
                || probability.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("Probability must be in [0,1]");
        }
        if (sorted.size() == 1) return sorted.getFirst();
        BigDecimal zeroBased = BigDecimal.valueOf(sorted.size() - 1).multiply(probability);
        int lowerIndex = zeroBased.intValue();
        int upperIndex = Math.min(lowerIndex + 1, sorted.size() - 1);
        BigDecimal fraction = zeroBased.subtract(BigDecimal.valueOf(lowerIndex));
        BigDecimal lower = sorted.get(lowerIndex);
        return lower.add(sorted.get(upperIndex).subtract(lower).multiply(fraction, MATH), MATH);
    }
}
