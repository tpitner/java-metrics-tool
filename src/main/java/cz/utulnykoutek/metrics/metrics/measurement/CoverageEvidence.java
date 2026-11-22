// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.metrics.measurement;

import java.util.Objects;

/** Resolution coverage for one well-defined dimension of a metric's input. */
public record CoverageEvidence(CoverageDimension dimension, long resolved, long total) {
    public CoverageEvidence {
        Objects.requireNonNull(dimension, "dimension");
        if (total < 0) {
            throw new IllegalArgumentException("Coverage total must not be negative");
        }
        if (resolved < 0 || resolved > total) {
            throw new IllegalArgumentException("Coverage resolved count must be between zero and total");
        }
    }

    public long unresolved() {
        return total - resolved;
    }

    public boolean complete() {
        return resolved == total;
    }
}
