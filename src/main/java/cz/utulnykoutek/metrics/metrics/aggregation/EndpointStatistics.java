// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.metrics.aggregation;

import java.util.Objects;

/** Descriptive statistics over all available endpoints of one direction. */
public record EndpointStatistics(DescriptiveStatistics statistics)
        implements EndpointAggregation {
    public EndpointStatistics {
        Objects.requireNonNull(statistics, "statistics");
    }
}
