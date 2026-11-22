// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.metrics.measurement;

import java.util.Objects;

/** The metric is defined for the scope but has no value for this particular subject. */
public record NotApplicableResult(ReasonCode reason) implements MeasurementResult {
    public NotApplicableResult {
        Objects.requireNonNull(reason, "reason");
    }

    @Override
    public MeasurementStatus status() {
        return MeasurementStatus.NOT_APPLICABLE;
    }
}
