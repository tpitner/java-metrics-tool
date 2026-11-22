// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.metrics.measurement;

/** Coarse status derived from the concrete measurement result. */
public enum MeasurementStatus {
    OK,
    PARTIAL,
    UNAVAILABLE,
    NOT_APPLICABLE
}
