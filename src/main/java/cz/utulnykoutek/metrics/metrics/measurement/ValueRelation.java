// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.metrics.measurement;

/** Mathematical relationship between a reported number and the complete metric value. */
public enum ValueRelation {
    /** The reported number is the complete metric value. */
    EXACT,
    /** The complete metric value is greater than or equal to the reported number. */
    AT_LEAST,
    /** The complete metric value is less than or equal to the reported number. */
    AT_MOST,
    /** The number describes only the resolved fragment and is not a bound on the complete value. */
    OBSERVED_FRAGMENT
}
