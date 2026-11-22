// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.config;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Reproducibility metadata for source and effective configuration. */
public record ConfigurationProvenance(
        Optional<Path> source,
        Optional<String> sourceSha256,
        String effectiveToml,
        String effectiveSha256,
        List<String> cliOverrides) {

    public ConfigurationProvenance {
        source = Objects.requireNonNull(source, "source");
        sourceSha256 = Objects.requireNonNull(sourceSha256, "sourceSha256");
        effectiveToml = Objects.requireNonNull(effectiveToml, "effectiveToml");
        effectiveSha256 = Objects.requireNonNull(effectiveSha256, "effectiveSha256");
        cliOverrides = List.copyOf(Objects.requireNonNull(cliOverrides, "cliOverrides"));
    }
}
