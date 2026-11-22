// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics;

import cz.utulnykoutek.metrics.metrics.definition.BuiltInMetricDefinition;
import cz.utulnykoutek.metrics.metrics.definition.MetricCatalog;
import cz.utulnykoutek.metrics.metrics.definition.MetricScope;
import cz.utulnykoutek.metrics.metrics.measurement.CoverageDimension;
import cz.utulnykoutek.metrics.metrics.measurement.CoverageEvidence;
import cz.utulnykoutek.metrics.metrics.measurement.IntervalResult;
import cz.utulnykoutek.metrics.metrics.measurement.MeasurementStatus;
import cz.utulnykoutek.metrics.metrics.measurement.MeasurementKey;
import cz.utulnykoutek.metrics.metrics.measurement.MeasurementStore;
import cz.utulnykoutek.metrics.metrics.measurement.MetricMeasurement;
import cz.utulnykoutek.metrics.metrics.measurement.NumericResult;
import cz.utulnykoutek.metrics.metrics.measurement.ReasonCode;
import cz.utulnykoutek.metrics.metrics.measurement.SubjectRef;
import cz.utulnykoutek.metrics.metrics.measurement.ValueRelation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetricMeasurementTest {

    @Test
    void exactMeasurementDerivesOkStatus() {
        MetricMeasurement measurement = new MetricMeasurement(
                BuiltInMetricDefinition.DIT.id(),
                new SubjectRef(MetricScope.TYPE, "type:example.Child"),
                "built-in-1",
                NumericResult.exact(new BigDecimal("3")),
                Map.of("lowInformation", "true"));

        assertEquals(MeasurementStatus.OK, measurement.status());
        assertEquals(ValueRelation.EXACT, ((NumericResult) measurement.result()).relation());
        assertEquals("true", measurement.attributes().get("lowInformation"));
        assertThrows(UnsupportedOperationException.class,
                () -> measurement.attributes().put("other", "value"));
    }

    @Test
    void lowerBoundExplainsWhyMeasurementIsPartial() {
        CoverageEvidence coverage = new CoverageEvidence(
                CoverageDimension.INHERITANCE_EDGES, 3, 4);
        NumericResult result = NumericResult.atLeast(
                new BigDecimal("3"),
                Set.of(ReasonCode.UNRESOLVED_SUPERTYPE),
                List.of(coverage));

        assertEquals(MeasurementStatus.PARTIAL, result.status());
        assertEquals(1, coverage.unresolved());
        assertTrue(result.reasons().contains(ReasonCode.UNRESOLVED_SUPERTYPE));
        assertThrows(UnsupportedOperationException.class,
                () -> result.coverage().add(new CoverageEvidence(CoverageDimension.TYPE_REFERENCES, 1, 1)));
    }

    @Test
    void rejectsContradictoryOrUnexplainedResults() {
        assertThrows(IllegalArgumentException.class, () -> new NumericResult(
                BigDecimal.ONE,
                ValueRelation.EXACT,
                Set.of(ReasonCode.INCOMPLETE_CLASSPATH),
                List.of()));
        assertThrows(IllegalArgumentException.class, () -> new NumericResult(
                BigDecimal.ONE,
                ValueRelation.AT_LEAST,
                Set.of(),
                List.of()));
        assertThrows(IllegalArgumentException.class, () -> new IntervalResult(
                BigDecimal.ONE,
                BigDecimal.ONE,
                Set.of(ReasonCode.UNRESOLVED_TYPE),
                List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new CoverageEvidence(CoverageDimension.METHOD_CALLS, 4, 3));
    }

    @Test
    void storeValidatesIndexesAndReturnsMeasurementsInStableOrder() {
        MetricCatalog catalog = MetricCatalog.builder()
                .register(BuiltInMetricDefinition.DIT)
                .register(BuiltInMetricDefinition.WMC)
                .build();
        SubjectRef child = new SubjectRef(MetricScope.TYPE, "type:example.Child");
        MetricMeasurement wmc = measurement(BuiltInMetricDefinition.WMC, child, "run-b", "5");
        MetricMeasurement dit = measurement(BuiltInMetricDefinition.DIT, child, "run-a", "2");

        MeasurementStore store = MeasurementStore.builder(catalog)
                .add(wmc)
                .add(dit)
                .build();

        assertEquals(List.of(dit, wmc), store.all());
        assertEquals(List.of(dit, wmc), store.forSubject(child));
        assertEquals(List.of(wmc), store.fromProviderRun("run-b"));
        assertEquals(dit, store.find(MeasurementKey.of(dit)).orElseThrow());
        assertThrows(UnsupportedOperationException.class, () -> store.all().add(dit));
    }

    @Test
    void storeRejectsDuplicatesUnsupportedScopesAndInvalidUnits() {
        MetricCatalog catalog = MetricCatalog.builder()
                .register(BuiltInMetricDefinition.DIT)
                .build();
        SubjectRef type = new SubjectRef(MetricScope.TYPE, "type:example.Child");
        MetricMeasurement valid = measurement(BuiltInMetricDefinition.DIT, type, "run-a", "2");

        MeasurementStore.Builder duplicateBuilder = MeasurementStore.builder(catalog).add(valid);
        assertThrows(IllegalArgumentException.class, () -> duplicateBuilder.add(valid));

        MetricMeasurement wrongScope = measurement(
                BuiltInMetricDefinition.DIT,
                new SubjectRef(MetricScope.PACKAGE, "package:example"),
                "run-a",
                "2");
        assertThrows(IllegalArgumentException.class,
                () -> MeasurementStore.builder(catalog).add(wrongScope));

        MetricMeasurement fractionalCount = measurement(
                BuiltInMetricDefinition.DIT, type, "run-a", "2.5");
        assertThrows(IllegalArgumentException.class,
                () -> MeasurementStore.builder(catalog).add(fractionalCount));
    }

    private static MetricMeasurement measurement(
            BuiltInMetricDefinition definition,
            SubjectRef subject,
            String providerRunId,
            String value) {
        return new MetricMeasurement(
                definition.id(),
                subject,
                providerRunId,
                NumericResult.exact(new BigDecimal(value)));
    }
}
