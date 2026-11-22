// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.metrics.measurement;

import java.util.Objects;
import java.util.Set;

/** No defensible value or bound could be computed, although the metric is applicable. */
public record UnavailableResult(Set<ReasonCode> reasons) implements MeasurementResult {
    public UnavailableResult {
        Objects.requireNonNull(reasons, "reasons");
        if (reasons.isEmpty()) {
            throw new IllegalArgumentException("An unavailable result must explain its cause");
        }
        reasons = Set.copyOf(reasons);
    }

    @Override
    public MeasurementStatus status() {
        return MeasurementStatus.UNAVAILABLE;
    }
}
