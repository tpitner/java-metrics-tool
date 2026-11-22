// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.export;

import cz.utulnykoutek.metrics.domain.Compound;
import cz.utulnykoutek.metrics.metrics.ProviderResult;
import cz.utulnykoutek.metrics.metrics.definition.BuiltInMetricDefinition;
import cz.utulnykoutek.metrics.metrics.definition.MetricScope;
import cz.utulnykoutek.metrics.metrics.measurement.MeasurementKey;
import cz.utulnykoutek.metrics.metrics.measurement.NumericResult;
import cz.utulnykoutek.metrics.metrics.measurement.SubjectRef;
import cz.utulnykoutek.metrics.metrics.measurement.ValueRelation;

import java.util.OptionalInt;

/** Presentation projection of the three canonical declared-member measurements. */
record DeclaredMemberCounts(OptionalInt methods, OptionalInt constructors, OptionalInt fields) {
    static DeclaredMemberCounts from(Compound type, ProviderResult providerResult) {
        String id = type.symbolId == null ? type.getId() : type.symbolId;
        SubjectRef subject = new SubjectRef(MetricScope.TYPE, id);
        return new DeclaredMemberCounts(
                exactCount(providerResult, BuiltInMetricDefinition.DECLARED_METHODS, subject),
                exactCount(providerResult, BuiltInMetricDefinition.DECLARED_CONSTRUCTORS, subject),
                exactCount(providerResult, BuiltInMetricDefinition.DECLARED_FIELDS, subject));
    }

    boolean isEmpty() {
        return methods.isEmpty() && constructors.isEmpty() && fields.isEmpty();
    }

    private static OptionalInt exactCount(
            ProviderResult providerResult,
            BuiltInMetricDefinition definition,
            SubjectRef subject) {
        MeasurementKey key = new MeasurementKey(
                definition.id(), subject, providerResult.run().providerRunId());
        var measurement = providerResult.measurements().find(key);
        if (measurement.isEmpty()) return OptionalInt.empty();
        if (!(measurement.orElseThrow().result() instanceof NumericResult numeric)
                || numeric.relation() != ValueRelation.EXACT) {
            throw new IllegalStateException(
                    "Declared-member measurement is not exact: " + definition.id() + " for " + subject.id());
        }
        return OptionalInt.of(numeric.value().intValueExact());
    }
}
