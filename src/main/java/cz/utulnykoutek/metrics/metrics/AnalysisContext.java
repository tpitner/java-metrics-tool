// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.metrics;

import cz.utulnykoutek.metrics.analysis.BaselineMetadata;
import cz.utulnykoutek.metrics.domain.PackageCompound;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Immutable project input shared by measurement providers. */
public record AnalysisContext(
        List<PackageCompound> packages,
        List<Path> sourceFiles,
        BaselineMetadata baselineMetadata) {

    public AnalysisContext {
        packages = List.copyOf(Objects.requireNonNull(packages, "packages"));
        sourceFiles = List.copyOf(Objects.requireNonNull(sourceFiles, "sourceFiles"));
        Objects.requireNonNull(baselineMetadata, "baselineMetadata");
    }
}
