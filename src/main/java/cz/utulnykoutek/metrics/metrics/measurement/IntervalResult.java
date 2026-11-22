// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.metrics.measurement;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Inclusive lower and upper bounds on a metric whose exact value is unknown. */
public record IntervalResult(
        BigDecimal lower,
        BigDecimal upper,
        Set<ReasonCode> reasons,
        List<CoverageEvidence> coverage) implements MeasurementResult {

    public IntervalResult {
        Objects.requireNonNull(lower, "lower");
        Objects.requireNonNull(upper, "upper");
        Objects.requireNonNull(reasons, "reasons");
        coverage = List.copyOf(Objects.requireNonNull(coverage, "coverage"));

        if (lower.compareTo(upper) >= 0) {
            throw new IllegalArgumentException(
                    "An interval result requires a lower bound strictly smaller than its upper bound");
        }
        if (reasons.isEmpty()) {
            throw new IllegalArgumentException("An interval result must explain its cause");
        }
        reasons = Set.copyOf(reasons);
    }

    @Override
    public MeasurementStatus status() {
        return MeasurementStatus.PARTIAL;
    }
}
