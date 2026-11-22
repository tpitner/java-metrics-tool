// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.metrics.aggregation;

import java.math.BigDecimal;
import java.util.Objects;

/** D12 descriptive statistics for a non-empty collection of exact values. */
public record DescriptiveStatistics(
        int count,
        BigDecimal sum,
        BigDecimal mean,
        BigDecimal median,
        BigDecimal minimum,
        BigDecimal maximum,
        BigDecimal p25,
        BigDecimal p75,
        BigDecimal iqr,
        BigDecimal p90,
        BigDecimal p95,
        BigDecimal p99) {

    public DescriptiveStatistics {
        if (count <= 0) throw new IllegalArgumentException("Statistics require at least one value");
        Objects.requireNonNull(sum, "sum");
        Objects.requireNonNull(mean, "mean");
        Objects.requireNonNull(median, "median");
        Objects.requireNonNull(minimum, "minimum");
        Objects.requireNonNull(maximum, "maximum");
        Objects.requireNonNull(p25, "p25");
        Objects.requireNonNull(p75, "p75");
        Objects.requireNonNull(iqr, "iqr");
        Objects.requireNonNull(p90, "p90");
        Objects.requireNonNull(p95, "p95");
        Objects.requireNonNull(p99, "p99");
    }
}
