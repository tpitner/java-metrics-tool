// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.metrics.measurement;

/** Numeric or non-numeric outcome of evaluating one metric for one subject. */
public sealed interface MeasurementResult
        permits NumericResult, IntervalResult, UnavailableResult, NotApplicableResult {
    MeasurementStatus status();
}
