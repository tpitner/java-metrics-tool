// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.metrics.aggregation;

import java.util.Objects;

/** Successfully computed primary descriptive statistics. */
public record AggregatedStatistics(DescriptiveStatistics statistics)
        implements AggregationResult {
    public AggregatedStatistics {
        Objects.requireNonNull(statistics, "statistics");
    }
}
