// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.metrics.aggregation;

/** Completeness counts for one metric, scope and provider-run aggregation. */
public record AggregationCounts(
        int nTotal,
        int nOk,
        int nPartial,
        int nNotApplicable,
        int nUnavailable) {

    public AggregationCounts {
        if (nTotal < 0 || nOk < 0 || nPartial < 0
                || nNotApplicable < 0 || nUnavailable < 0) {
            throw new IllegalArgumentException("Aggregation counts must not be negative");
        }
        if (nTotal != nOk + nPartial + nNotApplicable + nUnavailable) {
            throw new IllegalArgumentException("Aggregation status counts must sum to total");
        }
    }
}
