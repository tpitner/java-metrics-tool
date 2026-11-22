// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.metrics.measurement;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** A single reported number together with its relationship to the complete metric value. */
public record NumericResult(
        BigDecimal value,
        ValueRelation relation,
        Set<ReasonCode> reasons,
        List<CoverageEvidence> coverage) implements MeasurementResult {

    public NumericResult {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(relation, "relation");
        reasons = immutableReasons(reasons);
        coverage = List.copyOf(Objects.requireNonNull(coverage, "coverage"));

        if (relation == ValueRelation.EXACT && !reasons.isEmpty()) {
            throw new IllegalArgumentException("An exact result must not have incompleteness reasons");
        }
        if (relation != ValueRelation.EXACT && reasons.isEmpty()) {
            throw new IllegalArgumentException("An inexact result must explain its cause");
        }
    }

    public static NumericResult exact(BigDecimal value) {
        return new NumericResult(value, ValueRelation.EXACT, Set.of(), List.of());
    }

    public static NumericResult atLeast(BigDecimal value, Set<ReasonCode> reasons,
                                        List<CoverageEvidence> coverage) {
        return new NumericResult(value, ValueRelation.AT_LEAST, reasons, coverage);
    }

    public static NumericResult atMost(BigDecimal value, Set<ReasonCode> reasons,
                                       List<CoverageEvidence> coverage) {
        return new NumericResult(value, ValueRelation.AT_MOST, reasons, coverage);
    }

    @Override
    public MeasurementStatus status() {
        return relation == ValueRelation.EXACT ? MeasurementStatus.OK : MeasurementStatus.PARTIAL;
    }

    private static Set<ReasonCode> immutableReasons(Set<ReasonCode> reasons) {
        Objects.requireNonNull(reasons, "reasons");
        return reasons.isEmpty()
                ? Set.of()
                : Set.copyOf(reasons);
    }
}
