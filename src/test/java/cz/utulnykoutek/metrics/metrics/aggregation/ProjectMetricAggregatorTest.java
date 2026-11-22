// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.metrics.aggregation;

import cz.utulnykoutek.metrics.metrics.definition.BuiltInMetricDefinition;
import cz.utulnykoutek.metrics.metrics.definition.MetricCatalog;
import cz.utulnykoutek.metrics.metrics.definition.MetricScope;
import cz.utulnykoutek.metrics.metrics.measurement.MeasurementStore;
import cz.utulnykoutek.metrics.metrics.measurement.MetricMeasurement;
import cz.utulnykoutek.metrics.metrics.measurement.IntervalResult;
import cz.utulnykoutek.metrics.metrics.measurement.NotApplicableResult;
import cz.utulnykoutek.metrics.metrics.measurement.NumericResult;
import cz.utulnykoutek.metrics.metrics.measurement.ReasonCode;
import cz.utulnykoutek.metrics.metrics.measurement.SubjectRef;
import cz.utulnykoutek.metrics.metrics.measurement.UnavailableResult;
import cz.utulnykoutek.metrics.metrics.measurement.ValueRelation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class ProjectMetricAggregatorTest {
    private static final BuiltInMetricDefinition METRIC =
            BuiltInMetricDefinition.PHYSICAL_LOC;
    private static final String RUN = "java-metrics:test";

    @Test
    void computesTypeSevenStatisticsFromOkValuesAndReportsAllStatuses() {
        MeasurementStore store = store(List.of(
                exact("one", 1), exact("two", 2), exact("three", 3), exact("four", 4),
                measurement("partial", MetricScope.TYPE,
                        NumericResult.atLeast(BigDecimal.valueOf(5),
                                Set.of(ReasonCode.UNRESOLVED_TYPE), List.of())),
                measurement("na", MetricScope.TYPE,
                        new NotApplicableResult(ReasonCode.EMPTY_TYPE)),
                measurement("unavailable", MetricScope.TYPE,
                        new UnavailableResult(Set.of(ReasonCode.PROVIDER_FAILED))),
                measurement("other-scope", MetricScope.PACKAGE,
                        NumericResult.exact(BigDecimal.valueOf(100))),
                new MetricMeasurement(METRIC.id(), new SubjectRef(MetricScope.TYPE, "other-run"),
                        "java-metrics:other", NumericResult.exact(BigDecimal.valueOf(100)))));

        MetricAggregation aggregation = ProjectMetricAggregator.aggregate(
                store, METRIC.id(), MetricScope.TYPE, RUN);

        assertEquals(new AggregationCounts(7, 4, 1, 1, 1), aggregation.counts());
        DescriptiveStatistics statistics = assertInstanceOf(
                AggregatedStatistics.class, aggregation.result()).statistics();
        assertDecimal("10", statistics.sum());
        assertDecimal("2.5", statistics.mean());
        assertDecimal("2.5", statistics.median());
        assertDecimal("1", statistics.minimum());
        assertDecimal("4", statistics.maximum());
        assertDecimal("1.75", statistics.p25());
        assertDecimal("3.25", statistics.p75());
        assertDecimal("1.5", statistics.iqr());
        assertDecimal("3.7", statistics.p90());
        assertDecimal("3.85", statistics.p95());
        assertDecimal("3.97", statistics.p99());
    }

    @Test
    void returnsNoAggregatableValuesWhenNoOkMeasurementExists() {
        MeasurementStore store = store(List.of(
                measurement("partial", MetricScope.TYPE,
                        NumericResult.atLeast(BigDecimal.ZERO,
                                Set.of(ReasonCode.UNRESOLVED_TYPE), List.of())),
                measurement("na", MetricScope.TYPE,
                        new NotApplicableResult(ReasonCode.EMPTY_TYPE))));

        MetricAggregation aggregation = ProjectMetricAggregator.aggregate(
                store, METRIC.id(), MetricScope.TYPE, RUN);

        assertEquals(new AggregationCounts(2, 0, 1, 1, 0), aggregation.counts());
        assertInstanceOf(NoAggregatableValues.class, aggregation.result());
    }

    @Test
    void sensitivitySeparatesPartialIntervalAndObservedFragments() {
        MeasurementStore store = store(List.of(
                exact("one", 1), exact("two", 2),
                measurement("at-least", MetricScope.TYPE,
                        NumericResult.atLeast(BigDecimal.valueOf(5),
                                Set.of(ReasonCode.UNRESOLVED_TYPE), List.of())),
                measurement("at-most", MetricScope.TYPE,
                        NumericResult.atMost(BigDecimal.ZERO,
                                Set.of(ReasonCode.UNRESOLVED_TYPE), List.of())),
                measurement("observed", MetricScope.TYPE,
                        new NumericResult(BigDecimal.valueOf(10), ValueRelation.OBSERVED_FRAGMENT,
                                Set.of(ReasonCode.UNRESOLVED_TYPE), List.of())),
                measurement("interval", MetricScope.TYPE,
                        new IntervalResult(BigDecimal.valueOf(3), BigDecimal.valueOf(7),
                                Set.of(ReasonCode.UNRESOLVED_TYPE), List.of())),
                measurement("na", MetricScope.TYPE,
                        new NotApplicableResult(ReasonCode.EMPTY_TYPE)),
                measurement("unavailable", MetricScope.TYPE,
                        new UnavailableResult(Set.of(ReasonCode.PROVIDER_FAILED)))));

        MetricAggregation aggregation = ProjectMetricAggregator.aggregate(
                store, METRIC.id(), MetricScope.TYPE, RUN);

        assertEquals(new AggregationCounts(8, 2, 4, 1, 1), aggregation.counts());
        SensitivityAggregation sensitivity = aggregation.sensitivity();
        assertEquals(1, sensitivity.nObservedFragments());
        assertInstanceOf(EndpointStatistics.class, sensitivity.lowerEndpoints());
        assertInstanceOf(EndpointStatistics.class, sensitivity.upperEndpoints());

        DescriptiveStatistics lower = ((EndpointStatistics) sensitivity.lowerEndpoints()).statistics();
        DescriptiveStatistics upper = ((EndpointStatistics) sensitivity.upperEndpoints()).statistics();
        assertEquals(0, new BigDecimal("1").compareTo(lower.minimum()));
        assertEquals(0, new BigDecimal("7").compareTo(upper.maximum()));
        assertEquals(Set.of(ReasonCode.UNRESOLVED_TYPE, ReasonCode.PROVIDER_FAILED, ReasonCode.EMPTY_TYPE),
                sensitivity.reasons());
    }

    @Test
    void returnsNoEndpointValuesWhenNoPartialEndpointsExist() {
        MeasurementStore store = store(List.of(
                measurement("na", MetricScope.TYPE,
                        new NotApplicableResult(ReasonCode.EMPTY_TYPE)),
                measurement("observed", MetricScope.TYPE,
                        new NumericResult(BigDecimal.valueOf(10), ValueRelation.OBSERVED_FRAGMENT,
                                Set.of(ReasonCode.UNRESOLVED_TYPE), List.of()))));

        MetricAggregation aggregation = ProjectMetricAggregator.aggregate(
                store, METRIC.id(), MetricScope.TYPE, RUN);

        assertEquals(new NoEndpointValues(), aggregation.sensitivity().lowerEndpoints());
        assertEquals(new NoEndpointValues(), aggregation.sensitivity().upperEndpoints());
        assertEquals(1, aggregation.sensitivity().nObservedFragments());
    }

    @Test
    void typeSevenQuantileReturnsTheOnlyValueForSingleton() {
        assertDecimal("7", ProjectMetricAggregator.quantile(
                List.of(BigDecimal.valueOf(7)), new BigDecimal("0.99")));
    }

    private static MeasurementStore store(List<MetricMeasurement> measurements) {
        MetricCatalog catalog = MetricCatalog.builder().register(METRIC).build();
        return MeasurementStore.builder(catalog).addAll(measurements).build();
    }

    private static MetricMeasurement exact(String subject, int value) {
        return measurement(subject, MetricScope.TYPE,
                NumericResult.exact(BigDecimal.valueOf(value)));
    }

    private static MetricMeasurement measurement(
            String subject,
            MetricScope scope,
            cz.utulnykoutek.metrics.metrics.measurement.MeasurementResult result) {
        return new MetricMeasurement(METRIC.id(), new SubjectRef(scope, subject), RUN, result);
    }

    private static void assertDecimal(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
