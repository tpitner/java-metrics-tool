// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.metrics.aggregation;

import cz.utulnykoutek.metrics.metrics.measurement.CoverageEvidence;
import cz.utulnykoutek.metrics.metrics.measurement.ReasonCode;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Separate endpoint scenarios for incomplete measurements. */
public record SensitivityAggregation(
        EndpointAggregation lowerEndpoints,
        EndpointAggregation upperEndpoints,
        int nObservedFragments,
        Set<ReasonCode> reasons,
        List<CoverageEvidence> coverage) {

    public SensitivityAggregation {
        Objects.requireNonNull(lowerEndpoints, "lowerEndpoints");
        Objects.requireNonNull(upperEndpoints, "upperEndpoints");
        if (nObservedFragments < 0) {
            throw new IllegalArgumentException("Observed fragment count must not be negative");
        }
        reasons = Set.copyOf(Objects.requireNonNull(reasons, "reasons"));
        coverage = List.copyOf(Objects.requireNonNull(coverage, "coverage"));
    }
}
