// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.metrics;

import cz.utulnykoutek.metrics.metrics.measurement.MeasurementStore;

import java.util.List;
import java.util.Objects;

/** Measurements, provenance and diagnostics produced by one provider run. */
public record ProviderResult(
        ProviderRunMetadata run,
        MeasurementStore measurements,
        List<String> diagnostics) {

    public ProviderResult {
        Objects.requireNonNull(run, "run");
        Objects.requireNonNull(measurements, "measurements");
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
    }
}
